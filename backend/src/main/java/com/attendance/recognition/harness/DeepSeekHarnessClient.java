package com.attendance.recognition.harness;

import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.LayoutNormalizer;
import com.attendance.recognition.TableGridLocator;
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

    public DeepSeekHarnessClient(VisionGroundClient groundClient, RecognitionProperties properties,
                                 CropZoomCli cropZoomCli) {
        this.groundClient = groundClient;
        this.properties = properties;
        this.cropZoomCli = cropZoomCli;
    }

    public PageLayout groundPage(BufferedImage source, String countryCode, String apiKey) throws Exception {
        int previewWidth = Math.max(200, properties.getHarness().getPreviewWidth());
        byte[] preview = toPng(scaleToWidth(source, previewWidth));
        TableGridLocator.Grid grid = TableGridLocator.locate(source);
        if (grid.ok()) {
            PageLayout layout = new PageLayout();
            layout.sourceWidth = source.getWidth();
            layout.sourceHeight = source.getHeight();
            layout.source = "table_grid";
            layout.gridXs = grid.xs;
            layout.gridYs = grid.ys;
            layout.headerRows = grid.headerRows;
            layout.cells = TableGridLocator.toCells(grid);
            fillHeader(layout, preview, previewWidth, apiKey);
            LayoutNormalizer.normalize(layout);
            log.info("table_grid cols={} bodyRows={} cells={} xs={} warehouse={} dateRaw={}",
                    grid.columnCount(), grid.bodyRowCount(), layout.cells.size(),
                    java.util.Arrays.toString(grid.xs), layout.warehouse, layout.dateRaw);
            return layout;
        }
        log.warn("table_grid 未找到稳定格线，回退 vision_ground");
        PageLayout layout = groundClient.ground(preview, "image/png", previewWidth, countryCode, apiKey);
        layout.sourceWidth = source.getWidth();
        layout.sourceHeight = source.getHeight();
        layout.source = "vision_ground";
        int pad = properties.getHarness().getPadPx();
        List<GroundedCell> remapped = new ArrayList<GroundedCell>();
        for (GroundedCell cell : layout.cells) {
            if (cell.bbox == null) {
                continue;
            }
            BBox box = cell.bbox.remapFromPreview(previewWidth, source.getWidth())
                    .padded(pad, source.getWidth(), source.getHeight());
            remapped.add(new GroundedCell(cell.row, cell.field, box));
        }
        layout.cells = remapped;
        LayoutNormalizer.normalize(layout);
        log.info("vision_ground cells={}, warehouse={}, dateRaw={}",
                layout.cells.size(), layout.warehouse, layout.dateRaw);
        return layout;
    }

    private void fillHeader(PageLayout layout, byte[] preview, int previewWidth, String apiKey) {
        try {
            PageLayout header = groundClient.groundHeader(preview, "image/png", previewWidth, apiKey);
            if (header != null) {
                layout.warehouse = header.warehouse;
                layout.dateRaw = header.dateRaw;
                layout.pageNum = header.pageNum;
            }
        } catch (Exception e) {
            log.warn("页头 ground 失败: {}", e.getMessage());
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
