package com.attendance.recognition.harness;

import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.FlashKeyGate;
import com.attendance.recognition.LayoutNormalizer;
import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.CellCrop;
import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.PageLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Harness 切格：vision_ground 定位 + crop_zoom CLI 放大写出 PNG。
 * CLI 不可用或单格不合 crop-zoom 约束时，回退进程内 CropZoomTool。
 */
@Component
public class DeepSeekHarnessClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekHarnessClient.class);

    private final VisionGroundClient groundClient;
    private final RecognitionProperties properties;
    private final CropZoomCli cropZoomCli;
    private final FlashKeyGate keyGate;

    public DeepSeekHarnessClient(VisionGroundClient groundClient, RecognitionProperties properties,
                                 CropZoomCli cropZoomCli, FlashKeyGate keyGate) {
        this.groundClient = groundClient;
        this.properties = properties;
        this.cropZoomCli = cropZoomCli;
        this.keyGate = keyGate;
    }

    public PageLayout groundPage(BufferedImage source, String countryCode, String fallbackKey) throws Exception {
        int previewWidth = Math.max(200, properties.getHarness().getPreviewWidth());
        int bandCount = Math.max(1, properties.getHarness().getGroundBands());
        int pad = properties.getHarness().getPadPx();
        List<BandSpec> bands = splitBands(source.getHeight(), bandCount);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, bands.size()));
        List<Future<PageLayout>> futures = new ArrayList<Future<PageLayout>>();
        try {
            for (int i = 0; i < bands.size(); i++) {
                final BandSpec band = bands.get(i);
                final boolean top = i == 0;
                futures.add(pool.submit(new Callable<PageLayout>() {
                    @Override
                    public PageLayout call() throws Exception {
                        return groundBand(source, band, previewWidth, pad, countryCode, fallbackKey, top);
                    }
                }));
            }
            PageLayout merged = new PageLayout();
            merged.sourceWidth = source.getWidth();
            merged.sourceHeight = source.getHeight();
            merged.source = "vision_ground";
            merged.bandCount = bands.size();
            merged.previewWidth = previewWidth;
            for (int i = 0; i < futures.size(); i++) {
                PageLayout part = futures.get(i).get();
                if (part == null) {
                    continue;
                }
                if ((merged.warehouse == null || merged.warehouse.isEmpty()) && part.warehouse != null) {
                    merged.warehouse = part.warehouse;
                }
                if ((merged.dateRaw == null || merged.dateRaw.isEmpty()) && part.dateRaw != null) {
                    merged.dateRaw = part.dateRaw;
                }
                if ((merged.pageNum == null || merged.pageNum.isEmpty()) && part.pageNum != null) {
                    merged.pageNum = part.pageNum;
                }
                merged.cells.addAll(part.cells);
            }
            LayoutNormalizer.normalize(merged);
            log.info("vision_ground bands={} cells={} warehouse={} dateRaw={}",
                    bands.size(), merged.cells.size(), merged.warehouse, merged.dateRaw);
            return merged;
        } finally {
            pool.shutdown();
        }
    }

    private PageLayout groundBand(BufferedImage source, BandSpec band, int previewWidth, int pad,
                                  String countryCode, String fallbackKey, boolean keepHeader)
            throws Exception {
        BufferedImage slice = source.getSubimage(0, band.y0, source.getWidth(), band.h);
        byte[] preview = toPng(scaleToWidth(slice, previewWidth));
        FlashKeyGate.HeldKey held = keyGate.acquireOrFallback(fallbackKey);
        try {
            PageLayout layout = groundClient.ground(preview, "image/png", previewWidth, countryCode, held.apiKey());
            if (!keepHeader) {
                layout.warehouse = "";
                layout.dateRaw = "";
                layout.pageNum = "";
            }
            List<GroundedCell> remapped = new ArrayList<GroundedCell>();
            for (int i = 0; i < layout.cells.size(); i++) {
                GroundedCell cell = layout.cells.get(i);
                if (cell.bbox == null) {
                    continue;
                }
                BBox box = cell.bbox.remapFromPreview(previewWidth, source.getWidth());
                box.y = box.y + band.y0;
                box = box.padded(pad, source.getWidth(), source.getHeight());
                remapped.add(new GroundedCell(cell.row, cell.field, box));
            }
            layout.cells = remapped;
            return layout;
        } finally {
            held.close();
        }
    }

    static List<BandSpec> splitBands(int height, int bandCount) {
        List<BandSpec> bands = new ArrayList<BandSpec>();
        int n = Math.max(1, bandCount);
        if (n == 1 || height < 80) {
            bands.add(new BandSpec(0, height));
            return bands;
        }
        int step = Math.max(1, height / n);
        int bandH = Math.min(height, (int) Math.round(step * 1.20d));
        for (int i = 0; i < n; i++) {
            int y0 = Math.max(0, i * step - (int) Math.round(step * 0.10d));
            if (y0 + bandH > height) {
                y0 = Math.max(0, height - bandH);
            }
            bands.add(new BandSpec(y0, Math.min(bandH, height - y0)));
        }
        return bands;
    }

    static final class BandSpec {
        final int y0;
        final int h;

        BandSpec(int y0, int h) {
            this.y0 = y0;
            this.h = h;
        }
    }

    public List<CellCrop> cropZoom(BufferedImage source, PageLayout layout, Path workDir) throws Exception {
        List<CellCrop> crops = new ArrayList<CellCrop>();
        if (layout == null || layout.cells == null) {
            return crops;
        }
        int target = properties.getHarness().getZoomTarget();
        int maxScale = properties.getHarness().getZoomMaxScale();
        String mode = properties.getHarness().getZoomMode();
        Files.createDirectories(workDir);
        Path pagePng = workDir.resolve("page.png");
        ImageIO.write(source, "png", pagePng.toFile());

        List<GroundedCell> cliCells = new ArrayList<GroundedCell>();
        boolean cliOk = properties.getHarness().isPreferCli() && cropZoomCli.available();
        if (cliOk) {
            log.info("crop_zoom 使用 CLI {}", cropZoomCli.resolveBin());
        } else {
            log.info("crop_zoom 使用进程内 CropZoomTool");
        }
        for (GroundedCell cell : layout.cells) {
            if (cell.bbox == null || cell.field == null) {
                continue;
            }
            BBox cliBox = CropZoomCli.clampForCli(cell.bbox, source.getWidth(), source.getHeight());
            if (cliOk && cliBox != null) {
                cliCells.add(new GroundedCell(cell.row, cell.field, cliBox));
            } else {
                crops.add(cropJava(source, cell, workDir, target, maxScale, mode));
            }
        }
        List<List<GroundedCell>> batches = new ArrayList<List<GroundedCell>>();
        for (int i = 0; i < cliCells.size(); i += CropZoomCli.MAX_REGIONS) {
            int end = Math.min(i + CropZoomCli.MAX_REGIONS, cliCells.size());
            batches.add(new ArrayList<GroundedCell>(cliCells.subList(i, end)));
        }
        int cropThreads = Math.min(4, Math.max(1, batches.size()));
        ExecutorService pool = Executors.newFixedThreadPool(cropThreads);
        try {
            List<Future<List<CellCrop>>> futures = new ArrayList<Future<List<CellCrop>>>();
            for (int b = 0; b < batches.size(); b++) {
                final int batchIndex = b;
                final List<GroundedCell> batch = batches.get(b);
                futures.add(pool.submit(new Callable<List<CellCrop>>() {
                    @Override
                    public List<CellCrop> call() {
                        try {
                            return cropCliBatch(pagePng, batch, workDir, batchIndex, target, mode);
                        } catch (Exception e) {
                            log.warn("crop-zoom CLI 批次失败，回退 Java: {}", e.getMessage());
                            List<CellCrop> fallback = new ArrayList<CellCrop>();
                            for (int j = 0; j < batch.size(); j++) {
                                try {
                                    fallback.add(cropJava(source, batch.get(j), workDir, target, maxScale, mode));
                                } catch (Exception je) {
                                    log.warn("Java crop 失败 row={} field={}: {}",
                                            batch.get(j).row, batch.get(j).field, je.getMessage());
                                }
                            }
                            return fallback;
                        }
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                crops.addAll(futures.get(i).get());
            }
        } finally {
            pool.shutdown();
        }
        return crops;
    }

    private List<CellCrop> cropCliBatch(Path pagePng, List<GroundedCell> batch, Path workDir, int batchIndex,
                                        int target, String mode) throws Exception {
        List<BBox> boxes = new ArrayList<BBox>();
        for (int i = 0; i < batch.size(); i++) {
            boxes.add(batch.get(i).bbox);
        }
        Path stem = workDir.resolve("crop-b" + batchIndex + ".png");
        List<Path> files = cropZoomCli.crop(pagePng, boxes, stem, target, mode);
        List<CellCrop> crops = new ArrayList<CellCrop>();
        for (int i = 0; i < batch.size(); i++) {
            GroundedCell cell = batch.get(i);
            Path dest = workDir.resolve("r" + cell.row + "-" + sanitize(cell.field) + ".png");
            Path src = files.get(i);
            if (!src.equals(dest)) {
                Files.deleteIfExists(dest);
                Files.move(src, dest);
            }
            BufferedImage zoomed = ImageIO.read(dest.toFile());
            if (zoomed == null) {
                throw new IllegalStateException("无法读取 crop-zoom 输出 " + dest);
            }
            CellCrop crop = new CellCrop();
            crop.row = cell.row;
            crop.field = cell.field;
            crop.bbox = cell.bbox;
            crop.pngPath = dest;
            crop.image = zoomed;
            crops.add(crop);
        }
        return crops;
    }

    private static CellCrop cropJava(BufferedImage source, GroundedCell cell, Path workDir,
                                     int target, int maxScale, String mode) throws Exception {
        BufferedImage zoomed = CropZoomTool.cropZoom(source, cell.bbox, target, maxScale, mode);
        Path out = workDir.resolve("r" + cell.row + "-" + sanitize(cell.field) + ".png");
        ImageIO.write(zoomed, "png", out.toFile());
        CellCrop crop = new CellCrop();
        crop.row = cell.row;
        crop.field = cell.field;
        crop.bbox = cell.bbox;
        crop.pngPath = out;
        crop.image = zoomed;
        return crop;
    }

    public static BufferedImage decode(byte[] bytes) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IllegalArgumentException("无法解码考勤原图");
        }
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    static BufferedImage scaleToWidth(BufferedImage src, int targetW) {
        if (src.getWidth() <= targetW) {
            return src;
        }
        int h = Math.max(1, (int) Math.round(src.getHeight() * (targetW / (double) src.getWidth())));
        BufferedImage out = new BufferedImage(targetW, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, h, null);
        g.dispose();
        return out;
    }

    static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buf);
        return buf.toByteArray();
    }

    private static String sanitize(String field) {
        return field.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
