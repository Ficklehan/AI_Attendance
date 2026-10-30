package com.attendance.recognition.ink;

import com.attendance.config.RecognitionProperties;

import java.awt.image.BufferedImage;

/**
 * 去掉格内长斜线（划改），保留被线切开的局部笔画。
 */
public final class LongLineRemover {

    public static final class LineResult {
        public final boolean hasLongLine;
        public final BufferedImage cleaned;

        public LineResult(boolean hasLongLine, BufferedImage cleaned) {
            this.hasLongLine = hasLongLine;
            this.cleaned = cleaned;
        }
    }

    private LongLineRemover() {
    }

    public static LineResult remove(BufferedImage src, RecognitionProperties.Rule rule) {
        if (src == null) {
            return new LineResult(false, null);
        }
        int angleMin = rule != null ? rule.getLineAngleMin() : 20;
        int angleMax = rule != null ? rule.getLineAngleMax() : 75;
        int darkLum = rule != null ? rule.getDarkLuminance() : 165;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean[][] mask = new boolean[h][w];
        boolean found = false;
        double[] angles = new double[] {
                15, angleMin, (angleMin + angleMax) / 2.0d, angleMax, 80,
                -15, -angleMin, -(angleMin + angleMax) / 2.0d, -angleMax, -80
        };
        int diag = (int) Math.hypot(w, h);
        int minLen = Math.max(10, (int) Math.floor(diag * 0.32d));
        for (int a = 0; a < angles.length; a++) {
            double rad = Math.toRadians(angles[a]);
            double dx = Math.cos(rad);
            double dy = Math.sin(rad);
            for (int offset = -diag; offset <= diag; offset += 2) {
                int hits = 0;
                int samples = 0;
                int px = (int) Math.round(w / 2.0d + (-dy) * offset - dx * diag / 2.0d);
                int py = (int) Math.round(h / 2.0d + dx * offset - dy * diag / 2.0d);
                for (int i = 0; i < diag; i++) {
                    int x = px + (int) Math.round(dx * i);
                    int y = py + (int) Math.round(dy * i);
                    if (x < 0 || y < 0 || x >= w || y >= h) {
                        continue;
                    }
                    samples++;
                    if (CellInkAnalyzer.luminance(src.getRGB(x, y)) < darkLum) {
                        hits++;
                    }
                }
                if (samples >= minLen && hits >= samples * 0.42d && hits >= minLen * 0.35d) {
                    found = true;
                    paintLine(mask, w, h, px, py, dx, dy, diag, 2);
                }
            }
        }
        if (!found) {
            return new LineResult(false, copyRgb(src));
        }
        BufferedImage out = copyRgb(src);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y][x]) {
                    out.setRGB(x, y, neighborFill(src, mask, x, y, darkLum));
                }
            }
        }
        return new LineResult(true, out);
    }

    private static void paintLine(boolean[][] mask, int w, int h, int px, int py,
                                  double dx, double dy, int diag, int thickness) {
        for (int i = 0; i < diag; i++) {
            int cx = px + (int) Math.round(dx * i);
            int cy = py + (int) Math.round(dy * i);
            for (int t = -thickness; t <= thickness; t++) {
                int x = cx + (int) Math.round(-dy * t);
                int y = cy + (int) Math.round(dx * t);
                if (x >= 0 && y >= 0 && x < w && y < h) {
                    mask[y][x] = true;
                }
            }
        }
    }

    private static int neighborFill(BufferedImage src, boolean[][] mask, int x, int y, int darkLum) {
        int w = src.getWidth();
        int h = src.getHeight();
        long sum = 0;
        int n = 0;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                if (mask[ny][nx]) {
                    continue;
                }
                int rgb = src.getRGB(nx, ny);
                if (CellInkAnalyzer.luminance(rgb) < darkLum) {
                    continue;
                }
                sum += (rgb >> 16) & 0xff;
                sum += (rgb >> 8) & 0xff;
                sum += rgb & 0xff;
                n += 3;
            }
        }
        if (n == 0) {
            return 0x00F5F5F5;
        }
        int c = (int) (sum / n);
        return (0xFF << 24) | (c << 16) | (c << 8) | c;
    }

    private static BufferedImage copyRgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }
}
