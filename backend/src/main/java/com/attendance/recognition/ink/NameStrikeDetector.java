package com.attendance.recognition.ink;

import com.attendance.config.RecognitionProperties;

import java.awt.image.BufferedImage;

/**
 * 姓名格贯穿横线：中部水平暗带跨过大部分宽度。
 * 上下都还有墨 → 视为改名（有替换文字），不当整行删除。
 */
public final class NameStrikeDetector {

    public static final class StrikeResult {
        public final boolean struck;
        public final boolean hasReplacement;

        public StrikeResult(boolean struck, boolean hasReplacement) {
            this.struck = struck;
            this.hasReplacement = hasReplacement;
        }
    }

    private NameStrikeDetector() {
    }

    public static StrikeResult detect(BufferedImage image, RecognitionProperties.Rule rule) {
        if (image == null) {
            return new StrikeResult(false, false);
        }
        int darkLum = rule != null ? rule.getDarkLuminance() : 165;
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 8 || h < 8) {
            return new StrikeResult(false, false);
        }
        int y0 = (int) Math.floor(h * 0.38d);
        int y1 = Math.max(y0 + 1, (int) Math.floor(h * 0.62d));
        int bestRun = 0;
        for (int y = y0; y < y1; y++) {
            int run = 0;
            int maxRun = 0;
            for (int x = 0; x < w; x++) {
                if (CellInkAnalyzer.luminance(image.getRGB(x, y)) < darkLum) {
                    run++;
                    if (run > maxRun) {
                        maxRun = run;
                    }
                } else {
                    run = 0;
                }
            }
            if (maxRun > bestRun) {
                bestRun = maxRun;
            }
        }
        boolean struck = bestRun >= (int) Math.floor(w * 0.65d);
        boolean topInk = bandHasInk(image, 0, Math.max(1, (int) Math.floor(h * 0.32d)), darkLum);
        boolean bottomInk = bandHasInk(image, (int) Math.floor(h * 0.68d), h, darkLum);
        boolean hasReplacement = struck && topInk && bottomInk;
        return new StrikeResult(struck, hasReplacement);
    }

    private static boolean bandHasInk(BufferedImage image, int y0, int y1, int darkLum) {
        int w = image.getWidth();
        long dark = 0;
        long count = 0;
        int step = Math.max(1, w / 40);
        for (int y = y0; y < y1; y += Math.max(1, (y1 - y0) / 8)) {
            for (int x = 0; x < w; x += step) {
                count++;
                if (CellInkAnalyzer.luminance(image.getRGB(x, y)) < darkLum) {
                    dark++;
                }
            }
        }
        return count > 0 && dark / (double) count >= 0.04d;
    }
}
