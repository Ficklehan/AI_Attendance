package com.attendance.util;

import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * 上传前锐度检测：Laplacian 方差过低视为图片过糊。
 */
public final class ImageSharpnessAnalyzer {

    /** 低于此值认为不适合 OCR（经验阈值，可对清晰考勤表采样后微调） */
    public static final double MIN_LAPLACIAN_VARIANCE = 80.0;

    private static final int MAX_ANALYZE_EDGE = 1200;

    private ImageSharpnessAnalyzer() {
    }

    public static boolean isTooBlurry(byte[] fileBytes, String originalFilename, String contentType, Logger log,
                                       double minLaplacianVariance) {
        Double variance = measureLaplacianVariance(fileBytes, originalFilename, contentType, log);
        if (variance == null) {
            return false;
        }
        boolean blurry = variance < minLaplacianVariance;
        if (log != null) {
            log.info("图片锐度检测: variance={}, threshold={}, blurry={}, file={}",
                    variance, minLaplacianVariance, blurry, originalFilename);
        }
        return blurry;
    }

    public static boolean isTooBlurry(byte[] fileBytes, String originalFilename, String contentType, Logger log) {
        return isTooBlurry(fileBytes, originalFilename, contentType, log, MIN_LAPLACIAN_VARIANCE);
    }

    public static Double measureLaplacianVariance(byte[] fileBytes, String originalFilename, String contentType, Logger log) {
        if (fileBytes == null || fileBytes.length == 0) {
            return null;
        }
        try {
            BufferedImage image = loadPreviewImage(fileBytes, originalFilename, contentType);
            if (image == null) {
                if (log != null) {
                    log.warn("锐度检测无法解码图片: file={}", originalFilename);
                }
                return null;
            }
            BufferedImage gray = toGrayScale(downscaleIfNeeded(image));
            double score = combinedSharpnessScore(gray);
            if (log != null) {
                log.info("图片锐度检测: score={}, file={}", Math.round(score), originalFilename);
            }
            return score;
        } catch (Exception e) {
            if (log != null) {
                log.warn("锐度检测跳过: {}", e.getMessage());
            }
            return null;
        }
    }

    /** 聚焦画面中心区域，避免白边拉低/拉高整图方差；运动模糊会同时拉低中心锐度。 */
    private static double combinedSharpnessScore(BufferedImage gray) {
        double center = laplacianVariance(centerCrop(gray, 0.72));
        double global = laplacianVariance(gray);
        return Math.min(global, center);
    }

    private static BufferedImage centerCrop(BufferedImage source, double ratio) {
        double safeRatio = ratio <= 0 || ratio > 1 ? 1.0 : ratio;
        int w = source.getWidth();
        int h = source.getHeight();
        int cw = Math.max(1, (int) Math.round(w * safeRatio));
        int ch = Math.max(1, (int) Math.round(h * safeRatio));
        int x = Math.max(0, (w - cw) / 2);
        int y = Math.max(0, (h - ch) / 2);
        return source.getSubimage(x, y, cw, ch);
    }

    private static BufferedImage loadPreviewImage(byte[] fileBytes, String originalFilename, String contentType)
            throws IOException {
        if (ImageUploadValidator.isPdfUpload(fileBytes, originalFilename, contentType)) {
            List<byte[]> pages = PdfToImageConverter.toJpegPages(fileBytes, 1, 120f, null);
            if (pages.isEmpty()) {
                return null;
            }
            return ImageIO.read(new ByteArrayInputStream(pages.get(0)));
        }
        return ImageIO.read(new ByteArrayInputStream(fileBytes));
    }

    private static BufferedImage downscaleIfNeeded(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int maxEdge = Math.max(w, h);
        if (maxEdge <= MAX_ANALYZE_EDGE) {
            return source;
        }
        double scale = (double) MAX_ANALYZE_EDGE / maxEdge;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return scaled;
    }

    private static BufferedImage toGrayScale(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return source;
        }
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return gray;
    }

    private static double laplacianVariance(BufferedImage gray) {
        float[] kernelData = {
                0f, 1f, 0f,
                1f, -4f, 1f,
                0f, 1f, 0f
        };
        Kernel kernel = new Kernel(3, 3, kernelData);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage laplacian = op.filter(gray, null);

        int w = laplacian.getWidth();
        int h = laplacian.getHeight();
        long sum = 0;
        long sumSq = 0;
        int n = w * h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = laplacian.getRaster().getSample(x, y, 0);
                sum += v;
                sumSq += (long) v * v;
            }
        }
        double mean = (double) sum / n;
        return ((double) sumSq / n) - (mean * mean);
    }
}
