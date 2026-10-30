package com.attendance.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 识图前整页增强：不裁列、不裁行。去纸面白边后按 DeepSeek Flash 约 1300×1300
 * 像素预算缩放；加强纠偏、对比、纸白净化与锐化，让表格细字吃满单图视觉 token。
 */
public final class RecognitionImageEnhanceSupport {

    /** DeepSeek Flash 单图约 1300×1300 总像素预算。 */
    static final int MODEL_PIXEL_BUDGET = 1300 * 1300;
    static final int MAX_LONG_EDGE = 4096;
    static final float JPEG_QUALITY = 0.95f;
    /** 缩放后再锐化，抵消下采样发糊。 */
    static final float SHARPEN_AMOUNT = 0.58f;
    static final double MIN_DESKEW_DEGREES = 0.25d;
    static final double MAX_DESKEW_DEGREES = 6.0d;
    static final double DESKEW_COARSE_STEP = 0.5d;
    static final double DESKEW_FINE_STEP = 0.2d;
    /** 相对预算偏差小于此比例则不再缩放。 */
    static final double BUDGET_SCALE_EPS = 0.02d;
    /** 亮度高于此视为纸白候选，裁边更敢切。 */
    static final int MARGIN_PAPER_LUM = 248;
    /** 裁后仍占原图此比例以上则视为几乎无白边，跳过。 */
    static final double MARGIN_SKIP_KEEP_RATIO = 0.96d;
    static final double CONTRAST_TRIM_PCT = 0.01d;
    static final int CONTRAST_TARGET_LOW = 6;
    static final int CONTRAST_TARGET_SPAN = 248;

    private RecognitionImageEnhanceSupport() {
    }

    public static final class Result {
        private final byte[] bytes;
        private final String mimeType;
        private final boolean changed;
        private final double deskewDegrees;

        public Result(byte[] bytes, String mimeType, boolean changed, double deskewDegrees) {
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.changed = changed;
            this.deskewDegrees = deskewDegrees;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getMimeType() {
            return mimeType;
        }

        public boolean isChanged() {
            return changed;
        }

        public double getDeskewDegrees() {
            return deskewDegrees;
        }
    }

    public static Result enhance(byte[] imageBytes, String mimeType) {
        return enhance(imageBytes, mimeType, true);
    }

    /**
     * @param fitBudget true=压到 Flash ~1300²；false=保留高分辨率（供分条高清裁切）
     */
    public static Result enhance(byte[] imageBytes, String mimeType, boolean fitBudget) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new Result(imageBytes, mimeType, false, 0);
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (src == null || src.getWidth() < 8 || src.getHeight() < 8) {
                return new Result(imageBytes, mimeType, false, 0);
            }
            BufferedImage rgb = toRgb(src);
            int orientation = readJpegExifOrientation(imageBytes, mimeType);
            if (orientation != 1) {
                rgb = applyExifOrientation(rgb, orientation);
            }
            rgb = limitLongEdge(rgb);
            double deskew = estimateDeskewDegrees(rgb);
            if (Math.abs(deskew) >= MIN_DESKEW_DEGREES && Math.abs(deskew) <= MAX_DESKEW_DEGREES) {
                rgb = rotate(rgb, -deskew);
            } else {
                deskew = 0;
            }
            rgb = cropPaperMargin(rgb);
            // 先在较高分辨率拉对比/净化纸白；单图模式再压预算，分条模式保留高分辨率
            stretchContrast(rgb);
            whitenPaper(rgb);
            boostInk(rgb);
            if (fitBudget) {
                rgb = fitToModelPixelBudget(rgb);
            }
            rgb = sharpen(rgb);
            byte[] jpeg = encodeJpeg(rgb);
            if (jpeg == null || jpeg.length == 0) {
                return new Result(imageBytes, mimeType, false, 0);
            }
            return new Result(jpeg, "image/jpeg", true, deskew);
        } catch (Exception e) {
            return new Result(imageBytes, mimeType, false, 0);
        }
    }

    static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /**
     * 去掉大块白边，让整张表吃满模型约 1300×1300 预算。不切行、不切列。
     */
    static BufferedImage cropPaperMargin(BufferedImage src) {
        if (src == null) {
            return src;
        }
        int width = src.getWidth();
        int height = src.getHeight();
        if (width < 32 || height < 32) {
            return src;
        }
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int step = Math.max(1, Math.max(width, height) / 1600);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if (luminance(src.getRGB(x, y)) < MARGIN_PAPER_LUM) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return src;
        }
        // 少留白边，把像素预算留给表格本体
        int pad = Math.max(8, Math.min(width, height) / 64);
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(width - 1, maxX + pad);
        maxY = Math.min(height - 1, maxY + pad);
        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        if (cropW >= width * MARGIN_SKIP_KEEP_RATIO && cropH >= height * MARGIN_SKIP_KEEP_RATIO) {
            return src;
        }
        if (cropW < width * 0.40d || cropH < height * 0.40d) {
            return src;
        }
        BufferedImage dst = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, cropW, cropH);
            g.drawImage(src, 0, 0, cropW, cropH, minX, minY, minX + cropW, minY + cropH, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /**
     * 等比缩放到总像素贴近 {@link #MODEL_PIXEL_BUDGET}（可放大可缩小），
     * 避免白边裁完后吃不满 token，或大图交给模型再乱缩。
     */
    static BufferedImage fitToModelPixelBudget(BufferedImage src) {
        if (src == null) {
            return src;
        }
        int width = src.getWidth();
        int height = src.getHeight();
        long pixels = (long) width * (long) height;
        if (pixels <= 0) {
            return src;
        }
        double scale = Math.sqrt(MODEL_PIXEL_BUDGET / (double) pixels);
        if (Math.abs(scale - 1.0d) < BUDGET_SCALE_EPS) {
            return src;
        }
        int w = Math.max(1, (int) Math.round(width * scale));
        int h = Math.max(1, (int) Math.round(height * scale));
        return scaleImage(src, w, h);
    }

    static BufferedImage limitLongEdge(BufferedImage src) {
        int longEdge = Math.max(src.getWidth(), src.getHeight());
        if (longEdge <= MAX_LONG_EDGE) {
            return src;
        }
        double scale = MAX_LONG_EDGE / (double) longEdge;
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        return scaleImage(src, w, h);
    }

    private static BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    static double estimateDeskewDegrees(BufferedImage src) {
        int probeW = Math.min(720, src.getWidth());
        int probeH = Math.max(1, (int) Math.round(src.getHeight() * (probeW / (double) src.getWidth())));
        BufferedImage probe = new BufferedImage(probeW, probeH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = probe.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, probeW, probeH, null);
        } finally {
            g.dispose();
        }
        double bestAngle = bestDeskewAngle(probe, -MAX_DESKEW_DEGREES, MAX_DESKEW_DEGREES, DESKEW_COARSE_STEP);
        // 粗搜后再细搜，减少半度台阶带来的残余倾斜
        double fineLo = Math.max(-MAX_DESKEW_DEGREES, bestAngle - DESKEW_COARSE_STEP);
        double fineHi = Math.min(MAX_DESKEW_DEGREES, bestAngle + DESKEW_COARSE_STEP);
        bestAngle = bestDeskewAngle(probe, fineLo, fineHi, DESKEW_FINE_STEP);
        if (Math.abs(bestAngle) >= MAX_DESKEW_DEGREES - 1e-6) {
            return 0;
        }
        return bestAngle;
    }

    private static double bestDeskewAngle(BufferedImage probe, double from, double to, double step) {
        double bestAngle = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double angle = from; angle <= to + 1e-9; angle += step) {
            BufferedImage rotated = Math.abs(angle) < 1e-9 ? probe : rotate(probe, -angle);
            double score = horizontalInkVariance(rotated);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    static double horizontalInkVariance(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        double[] sums = new double[h];
        double mean = 0;
        for (int y = 0; y < h; y++) {
            double ink = 0;
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int lum = luminance(rgb);
                if (lum < 210) {
                    ink += (210 - lum);
                }
            }
            sums[y] = ink;
            mean += ink;
        }
        mean /= h;
        double var = 0;
        for (int y = 0; y < h; y++) {
            double d = sums[y] - mean;
            var += d * d;
        }
        return var / h;
    }

    static BufferedImage rotate(BufferedImage src, double degrees) {
        double rad = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(rad));
        double cos = Math.abs(Math.cos(rad));
        int w = src.getWidth();
        int h = src.getHeight();
        int nw = Math.max(1, (int) Math.floor(w * cos + h * sin));
        int nh = Math.max(1, (int) Math.floor(w * sin + h * cos));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            AffineTransform at = new AffineTransform();
            at.translate((nw - w) / 2.0d, (nh - h) / 2.0d);
            at.rotate(rad, w / 2.0d, h / 2.0d);
            g.drawImage(src, at, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    static void stretchContrast(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int step = Math.max(1, Math.max(w, h) / 1000);
        int[] hist = new int[256];
        int samples = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                hist[luminance(image.getRGB(x, y))]++;
                samples++;
            }
        }
        if (samples < 16) {
            return;
        }
        int lowCount = Math.max(1, (int) Math.round(samples * CONTRAST_TRIM_PCT));
        int highCount = Math.max(1, (int) Math.round(samples * CONTRAST_TRIM_PCT));
        int low = 0;
        int seen = 0;
        for (int i = 0; i < 256; i++) {
            seen += hist[i];
            if (seen >= lowCount) {
                low = i;
                break;
            }
        }
        int high = 255;
        seen = 0;
        for (int i = 255; i >= 0; i--) {
            seen += hist[i];
            if (seen >= highCount) {
                high = i;
                break;
            }
        }
        if (high - low < 40) {
            return;
        }
        double scale = CONTRAST_TARGET_SPAN / (double) (high - low);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = clamp((int) Math.round((((rgb >> 16) & 0xff) - low) * scale + CONTRAST_TARGET_LOW));
                int gch = clamp((int) Math.round((((rgb >> 8) & 0xff) - low) * scale + CONTRAST_TARGET_LOW));
                int b = clamp((int) Math.round(((rgb & 0xff) - low) * scale + CONTRAST_TARGET_LOW));
                image.setRGB(x, y, (r << 16) | (gch << 8) | b);
            }
        }
    }

    /** 近白像素拉向纯白，减少灰底抢对比、也让格线/字迹相对更干净。 */
    static void whitenPaper(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int lum = luminance(rgb);
                if (lum < 228) {
                    continue;
                }
                // 228→保持，255→纯白，中间平滑抬升
                double t = (lum - 228) / 27.0d;
                if (t > 1.0d) {
                    t = 1.0d;
                }
                double lift = 0.55d + 0.45d * t;
                int r = clamp((int) Math.round(((rgb >> 16) & 0xff) * (1.0d - lift) + 255 * lift));
                int gch = clamp((int) Math.round(((rgb >> 8) & 0xff) * (1.0d - lift) + 255 * lift));
                int b = clamp((int) Math.round((rgb & 0xff) * (1.0d - lift) + 255 * lift));
                image.setRGB(x, y, (r << 16) | (gch << 8) | b);
            }
        }
    }

    /** 压暗中灰墨迹，手写/打印细字更清晰，不伤近白纸面。 */
    static void boostInk(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int lum = luminance(rgb);
                if (lum <= 28 || lum >= 210) {
                    continue;
                }
                // 中间调略压暗：越接近字迹区压得越多
                double strength = lum < 120
                        ? 0.18d
                        : 0.18d * ((210 - lum) / 90.0d);
                double factor = 1.0d - strength;
                int r = clamp((int) Math.round(((rgb >> 16) & 0xff) * factor));
                int gch = clamp((int) Math.round(((rgb >> 8) & 0xff) * factor));
                int b = clamp((int) Math.round((rgb & 0xff) * factor));
                image.setRGB(x, y, (r << 16) | (gch << 8) | b);
            }
        }
    }

    static BufferedImage sharpen(BufferedImage src) {
        float a = SHARPEN_AMOUNT;
        float center = 1.0f + 4.0f * a;
        float[] kernel = new float[]{
                0, -a, 0,
                -a, center, -a,
                0, -a, 0
        };
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, dst.getWidth(), dst.getHeight());
        } finally {
            g.dispose();
        }
        op.filter(src, dst);
        return dst;
    }

    static int readJpegExifOrientation(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length < 12) {
            return 1;
        }
        if (mimeType != null && !mimeType.toLowerCase().contains("jpeg") && !mimeType.toLowerCase().contains("jpg")) {
            if (!(bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8)) {
                return 1;
            }
        }
        try {
            return parseJpegOrientation(bytes);
        } catch (Exception e) {
            return 1;
        }
    }

    static BufferedImage applyExifOrientation(BufferedImage src, int orientation) {
        switch (orientation) {
            case 3:
                return rotate(src, 180);
            case 6:
                return rotate(src, 90);
            case 8:
                return rotate(src, -90);
            default:
                return src;
        }
    }

    private static int parseJpegOrientation(byte[] bytes) {
        int i = 2;
        while (i + 4 < bytes.length && (bytes[i] & 0xFF) == 0xFF) {
            int marker = bytes[i + 1] & 0xFF;
            int len = ((bytes[i + 2] & 0xFF) << 8) | (bytes[i + 3] & 0xFF);
            if (marker == 0xE1 && i + 4 + 6 < bytes.length) {
                if (bytes[i + 4] == 'E' && bytes[i + 5] == 'x' && bytes[i + 6] == 'i' && bytes[i + 7] == 'f'
                        && bytes[i + 8] == 0 && bytes[i + 9] == 0) {
                    return parseTiffOrientation(bytes, i + 10);
                }
            }
            if (len < 2) {
                break;
            }
            i += 2 + len;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] bytes, int tiffStart) {
        if (tiffStart + 8 >= bytes.length) {
            return 1;
        }
        boolean little = bytes[tiffStart] == 'I' && bytes[tiffStart + 1] == 'I';
        boolean big = bytes[tiffStart] == 'M' && bytes[tiffStart + 1] == 'M';
        if (!little && !big) {
            return 1;
        }
        int ifd = tiffStart + readU32(bytes, tiffStart + 4, little);
        if (ifd + 2 >= bytes.length) {
            return 1;
        }
        int entries = readU16(bytes, ifd, little);
        for (int e = 0; e < entries; e++) {
            int off = ifd + 2 + e * 12;
            if (off + 12 > bytes.length) {
                break;
            }
            int tag = readU16(bytes, off, little);
            if (tag == 0x0112) {
                int value = readU16(bytes, off + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int readU16(byte[] bytes, int off, boolean little) {
        int a = bytes[off] & 0xFF;
        int b = bytes[off + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static int readU32(byte[] bytes, int off, boolean little) {
        int a = bytes[off] & 0xFF;
        int b = bytes[off + 1] & 0xFF;
        int c = bytes[off + 2] & 0xFF;
        int d = bytes[off + 3] & 0xFF;
        return little ? (d << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | d;
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static int clamp(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 255) {
            return 255;
        }
        return v;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            ImageOutputStream ios = ImageIO.createImageOutputStream(out);
            try {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
                ios.flush();
            } finally {
                ios.close();
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            writer.dispose();
        }
    }
}
