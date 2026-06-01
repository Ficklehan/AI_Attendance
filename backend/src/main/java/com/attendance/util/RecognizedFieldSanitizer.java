package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

/**
 * 识别结果可选字段清洗：未识别到的占位符统一为空，不做默认回填。
 */
public final class RecognizedFieldSanitizer {

    private RecognizedFieldSanitizer() {
    }

    public static boolean isUnrecognized(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed) || "—".equals(trimmed)) {
            return true;
        }
        if ("???".equals(trimmed) || "??".equals(trimmed)) {
            return true;
        }
        return "illegible".equalsIgnoreCase(trimmed)
                || "n/a".equalsIgnoreCase(trimmed)
                || "na".equalsIgnoreCase(trimmed)
                || "null".equalsIgnoreCase(trimmed)
                || "none".equalsIgnoreCase(trimmed);
    }

    public static String sanitizeOptionalText(String value) {
        return isUnrecognized(value) ? "" : value.trim();
    }

    /** 仓库仅保留图片中识别到的值，未识别则留空。 */
    public static void sanitizeWarehouse(JSONObject record) {
        if (record == null) {
            return;
        }
        record.put("Entrepot", sanitizeOptionalText(record.getString("Entrepot")));
    }
}
