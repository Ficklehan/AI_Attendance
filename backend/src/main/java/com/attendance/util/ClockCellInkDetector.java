package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 到离格墨迹二次校验：模型把有连笔的格子标成「未出勤」时，用灰度方差拦住并改为「模糊」待复核。
 * <p>
 * 不要求精确列切图。按表体行带抽样到达/离开所在的中右区域；印刷冒号的暗像素很少，狂草连笔会拉高方差。
 */
public final class ClockCellInkDetector {

    /** 暗像素占比：高于此值视为有手写墨迹（印刷冒号通常远低于此）。 */
    static final double DARK_RATIO_THRESHOLD = 0.028d;

    /** 亮度方差：空白格接近 0，连笔字迹明显升高。 */
    static final double LUMINANCE_VARIANCE_THRESHOLD = 280d;

    static final int DARK_LUMINANCE = 165;

    private ClockCellInkDetector() {
    }

    public static int overrideAbsentWhenClockInkPresent(BufferedImage image, List<JSONObject> records) {
        if (image == null || records == null || records.isEmpty()) {
            return 0;
        }
        int changed = 0;
        int n = records.size();
        for (int i = 0; i < n; i++) {
            JSONObject record = records.get(i);
            if (!shouldInspect(record)) {
                continue;
            }
            if (!hasClockInk(image, i, n)) {
                continue;
            }
            forceBlurReview(record);
            changed++;
        }
        return changed;
    }

    static boolean shouldInspect(JSONObject record) {
        if (record == null || RowReadabilitySupport.isDeletedRow(record)) {
            return false;
        }
        if (Boolean.TRUE.equals(record.getBoolean("_manuallyAdded"))) {
            return false;
        }
        if (RowReadabilitySupport.hasBlurMark(record)) {
            return false;
        }
        if (RowReadabilitySupport.hasRecognizedPunchTime(record)) {
            return false;
        }
        return RowReadabilitySupport.containsAnyToken(
                safe(record.getString("SmartMark")) + ";" + safe(record.getString("Mark")),
                java.util.Arrays.asList("未出勤", "absent"));
    }

    static boolean hasClockInk(BufferedImage image, int rowIndex, int rowCount) {
        if (image == null || rowCount <= 0 || rowIndex < 0 || rowIndex >= rowCount) {
            return false;
        }
        InkStats stats = sampleClockBand(image, rowIndex, rowCount);
        return stats.darkRatio >= DARK_RATIO_THRESHOLD
                || (stats.variance >= LUMINANCE_VARIANCE_THRESHOLD && stats.darkRatio >= 0.012d);
    }

    static InkStats sampleClockBand(BufferedImage image, int rowIndex, int rowCount) {
        int w = image.getWidth();
        int h = image.getHeight();
        int tableTop = (int) Math.floor(h * 0.30d);
        int tableBottom = Math.max(tableTop + 8, (int) Math.floor(h * 0.98d));
        int x0 = (int) Math.floor(w * 0.48d);
        int x1 = Math.max(x0 + 4, (int) Math.floor(w * 0.78d));
        int slots = rowCount + 1;
        int band = Math.max(4, (tableBottom - tableTop) / slots);
        int y0 = tableTop + band * (rowIndex + 1);
        int y1 = Math.min(tableBottom, y0 + band);
        if (y1 <= y0) {
            return InkStats.EMPTY;
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
                if (lum < DARK_LUMINANCE) {
                    dark++;
                }
            }
        }
        if (count == 0) {
            return InkStats.EMPTY;
        }
        double mean = sum / count;
        double variance = (sumSq / count) - mean * mean;
        return new InkStats(dark / (double) count, Math.max(0, variance));
    }

    static void forceBlurReview(JSONObject record) {
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        record.put("Mark", "模糊");
        record.put("SmartMark", "模糊");
        record.put("_reviewReason", "clock_ink");
        record.put("ExceptionType", "");
    }

    static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class InkStats {
        static final InkStats EMPTY = new InkStats(0, 0);
        final double darkRatio;
        final double variance;

        InkStats(double darkRatio, double variance) {
            this.darkRatio = darkRatio;
            this.variance = variance;
        }
    }
}
