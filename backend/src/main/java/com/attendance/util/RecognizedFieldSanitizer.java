package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Map;

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
                || "unknown".equalsIgnoreCase(trimmed)
                || "n/a".equalsIgnoreCase(trimmed)
                || "na".equalsIgnoreCase(trimmed)
                || "null".equalsIgnoreCase(trimmed)
                || "none".equalsIgnoreCase(trimmed);
    }

    private static final String[] RECORD_TEXT_KEYS = {
            "Pays", "Entrepot", "Date", "WorkDate", "NOM_PRENOM", "Name", "NO",
            "AGENCE_INTERIMAIRE", "HORAIRES_DU_TRAVAIL", "ARRIVEE", "DEPAR", "DEPART",
            "Observations", "PAGE_NUM", "pageNum"
    };

    /** 确认提交前将占位符归一为空字符串（不含 PAUSE、签名列）。 */
    public static void sanitizeRecordPlaceholders(Map<String, Object> record) {
        if (record == null) {
            return;
        }
        for (String key : RECORD_TEXT_KEYS) {
            if (!record.containsKey(key)) {
                continue;
            }
            Object value = record.get(key);
            if (value == null) {
                continue;
            }
            record.put(key, sanitizeOptionalText(String.valueOf(value)));
        }
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
