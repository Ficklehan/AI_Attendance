package com.attendance.recognition.ink;

import com.attendance.config.RecognitionProperties;

import java.awt.image.BufferedImage;

/**
 * 格内墨迹：暗像素比 + 亮度方差。印刷冒号通常远低于阈值。
 */
public final class CellInkAnalyzer {

    public static final class InkStats {
        public final double darkRatio;
        public final double variance;
        public final boolean hasInk;

        InkStats(double darkRatio, double variance, boolean hasInk) {
            this.darkRatio = darkRatio;
            this.variance = variance;
            this.hasInk = hasInk;
        }
    }

    private CellInkAnalyzer() {
    }

    public static InkStats analyze(BufferedImage image, RecognitionProperties.Rule rule) {
        return analyzeRegion(image, 0, 0,
                image == null ? 0 : image.getWidth(),
                image == null ? 0 : image.getHeight(),
                rule, true);
    }

    /** 数字位：不用方差，避免空格虚线当墨。 */
    public static InkStats analyzeGlyph(BufferedImage image, RecognitionProperties.Rule rule) {
        return analyzeRegion(image, 0, 0,
                image == null ? 0 : image.getWidth(),
                image == null ? 0 : image.getHeight(),
                rule, false);
    }

    /**
     * 到离格：先去掉贯通格线/四宫格，再看冒号左右。印刷线、冒号不算手写墨。
     */
    public static InkStats analyzeClock(BufferedImage image, RecognitionProperties.Rule rule) {
        if (image == null) {
            return new InkStats(0, 0, false);
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 8 || h < 4) {
            return new InkStats(0, 0, false);
        }
        int darkLum = rule != null ? rule.getDarkLuminance() : 165;
        BufferedImage cleaned = maskAxisRuling(image, darkLum);
        int leftW = Math.max(1, (int) Math.floor(w * 0.42d));
        int rightX = Math.min(w - 1, (int) Math.floor(w * 0.58d));
        InkStats left = analyzeRegion(cleaned, 0, 0, leftW, h, rule, false);
        InkStats right = analyzeRegion(cleaned, rightX, 0, w - rightX, h, rule, false);
        boolean hasInk = left.hasInk || right.hasInk;
        double darkRatio = Math.max(left.darkRatio, right.darkRatio);
        double variance = Math.max(left.variance, right.variance);
        return new InkStats(darkRatio, variance, hasInk);
    }

    /** 贯通竖线/横线（表线、四宫格）涂白，留下笔画。 */
    static BufferedImage maskAxisRuling(BufferedImage src, int darkLum) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean[] col = new boolean[w];
        boolean[] row = new boolean[h];
        for (int x = 0; x < w; x++) {
            int dark = 0;
            for (int y = 0; y < h; y++) {
                if (luminance(src.getRGB(x, y)) < darkLum) {
                    dark++;
                }
            }
            if (dark / (double) h >= 0.70d) {
                col[x] = true;
            }
        }
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (luminance(src.getRGB(x, y)) < darkLum) {
                    dark++;
                }
            }
            if (dark / (double) w >= 0.70d) {
                row[y] = true;
            }
        }
        boolean[] colDilate = dilate(col);
        boolean[] rowDilate = dilate(row);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (colDilate[x] || rowDilate[y]) {
                    out.setRGB(x, y, 0x00FFFFFF);
                } else {
                    out.setRGB(x, y, src.getRGB(x, y));
                }
            }
        }
        return out;
    }

    private static boolean[] dilate(boolean[] src) {
        boolean[] out = new boolean[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i]
                    || (i > 0 && src[i - 1])
                    || (i + 1 < src.length && src[i + 1]);
        }
        return out;
    }

    static InkStats analyzeRegion(BufferedImage image, int x0, int y0, int rw, int rh,
                                  RecognitionProperties.Rule rule, boolean useVariance) {
        if (image == null || rw < 1 || rh < 1) {
            return new InkStats(0, 0, false);
        }
        double darkRatioTh = rule != null ? rule.getInkDarkRatio() : 0.028d;
        double varianceTh = rule != null ? rule.getInkVariance() : 280d;
        int darkLum = rule != null ? rule.getDarkLuminance() : 165;
        int x1 = Math.min(image.getWidth(), x0 + rw);
        int y1 = Math.min(image.getHeight(), y0 + rh);
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        if (x1 <= x0 || y1 <= y0) {
            return new InkStats(0, 0, false);
        }
        long count = 0;
        long dark = 0;
        double sum = 0;
        double sumSq = 0;
        int stepX = Math.max(1, (x1 - x0) / 80);
        int stepY = Math.max(1, (y1 - y0) / 40);
        for (int y = y0; y < y1; y += stepY) {
            for (int x = x0; x < x1; x += stepX) {
                int lum = luminance(image.getRGB(x, y));
                count++;
                sum += lum;
                sumSq += (double) lum * lum;
                if (lum < darkLum) {
                    dark++;
                }
            }
        }
        if (count == 0) {
            return new InkStats(0, 0, false);
        }
        double mean = sum / count;
        double variance = Math.max(0, (sumSq / count) - mean * mean);
        double darkRatio = dark / (double) count;
        boolean hasInk = darkRatio >= darkRatioTh
                || (useVariance && variance >= varianceTh && darkRatio >= 0.012d);
        return new InkStats(darkRatio, variance, hasInk);
    }

    public static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
