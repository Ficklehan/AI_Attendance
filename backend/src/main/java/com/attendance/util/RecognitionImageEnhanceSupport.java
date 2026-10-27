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
 * 识图前整页增强：不裁列、不裁行。摆正小倾角、拉开对比、轻度锐化，
 * 让铅笔细笔在模型缩到约 800×800 后仍能看见。
 */
public final class RecognitionImageEnhanceSupport {

    static final int MAX_LONG_EDGE = 4096;
    static final float JPEG_QUALITY = 0.92f;
    static final float SHARPEN_AMOUNT = 0.35f;
    static final double MIN_DESKEW_DEGREES = 0.4d;
    static final double MAX_DESKEW_DEGREES = 5.0d;

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
            stretchContrast(rgb);
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

    static BufferedImage limitLongEdge(BufferedImage src) {
        int longEdge = Math.max(src.getWidth(), src.getHeight());
        if (longEdge <= MAX_LONG_EDGE) {
            return src;
        }
        double scale = MAX_LONG_EDGE / (double) longEdge;
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    static double estimateDeskewDegrees(BufferedImage src) {
        int probeW = Math.min(640, src.getWidth());
        int probeH = Math.max(1, (int) Math.round(src.getHeight() * (probeW / (double) src.getWidth())));
        BufferedImage probe = new BufferedImage(probeW, probeH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = probe.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, probeW, probeH, null);
        } finally {
            g.dispose();
        }
        double bestAngle = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double angle = -MAX_DESKEW_DEGREES; angle <= MAX_DESKEW_DEGREES + 1e-6; angle += 0.5d) {
            BufferedImage rotated = Math.abs(angle) < 1e-6 ? probe : rotate(probe, -angle);
            double score = horizontalInkVariance(rotated);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        if (Math.abs(bestAngle) >= MAX_DESKEW_DEGREES - 1e-6) {
            return 0;
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
        int step = Math.max(1, Math.max(w, h) / 800);
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
        int lowCount = Math.max(1, (int) Math.round(samples * 0.02d));
        int highCount = Math.max(1, (int) Math.round(samples * 0.02d));
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
        if (high - low < 48) {
            return;
        }
        double scale = 230.0d / (high - low);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = clamp((int) Math.round((((rgb >> 16) & 0xff) - low) * scale + 12));
                int gch = clamp((int) Math.round((((rgb >> 8) & 0xff) - low) * scale + 12));
                int b = clamp((int) Math.round(((rgb & 0xff) - low) * scale + 12));
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
