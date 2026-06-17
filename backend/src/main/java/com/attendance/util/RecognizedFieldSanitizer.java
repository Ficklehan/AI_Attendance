package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 识别结果可选字段清洗：未识别到的占位符统一为空，不做默认回填。
 * 模型输出的 ??? / illegible 记入 {@code _unreadableFields}，不写入单元格值。
 */
public final class RecognizedFieldSanitizer {

    public static final String UNREADABLE_FIELDS_KEY = "_unreadableFields";

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

    /** 模型明确标为无法辨认（不含空白） */
    public static boolean isExplicitUnreadable(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return "???".equals(trimmed)
                || "??".equals(trimmed)
                || "illegible".equalsIgnoreCase(trimmed)
                || "unknown".equalsIgnoreCase(trimmed);
    }

    private static final String[] TIME_FIELD_KEYS = {
            "HORAIRES_DU_TRAVAIL", "ARRIVEE", "DEPAR", "DEPART"
    };

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
        record.remove(UNREADABLE_FIELDS_KEY);
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

    /**
     * 识别结果入库：收集看不清字段到元数据，单元格值清空；必要时补「模糊」标记。
     */
    public static void annotateAndSanitizeRecord(JSONObject record) {
        if (record == null) {
            return;
        }
        Set<String> unreadable = new LinkedHashSet<>();
        JSONArray existing = record.getJSONArray(UNREADABLE_FIELDS_KEY);
        if (existing != null) {
            for (int i = 0; i < existing.size(); i++) {
                Object item = existing.get(i);
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    unreadable.add(String.valueOf(item).trim());
                }
            }
        }
        for (String key : RECORD_TEXT_KEYS) {
            if (!record.containsKey(key)) {
                continue;
            }
            String raw = record.getString(key);
            if (isExplicitUnreadable(raw) || isTimeFieldNonTimeLabel(key, raw)) {
                unreadable.add(key);
                record.put(key, "");
            } else {
                record.put(key, sanitizeOptionalText(raw));
            }
        }
        if (record.containsKey("PAUSE")) {
            Object pauseValue = record.get("PAUSE");
            String pauseText = pauseValue == null ? "" : String.valueOf(pauseValue).trim();
            if (isExplicitUnreadable(pauseText)) {
                unreadable.add("PAUSE");
                record.put("PAUSE", "");
            } else if (isUnrecognized(pauseText)) {
                record.put("PAUSE", "");
            }
        }
        if (!unreadable.isEmpty()) {
            JSONArray fields = new JSONArray();
            fields.addAll(unreadable);
            record.put(UNREADABLE_FIELDS_KEY, fields);
            appendBlurMarkIfNeeded(record);
        } else {
            record.remove(UNREADABLE_FIELDS_KEY);
        }
    }

    private static void appendBlurMarkIfNeeded(JSONObject record) {
        if (record.getBooleanValue("isDeleted")) {
            return;
        }
        String smartMark = record.getString("SmartMark");
        if (smartMark != null) {
            if (smartMark.contains("未出勤") || smartMark.contains("已删除") || smartMark.contains("模糊")) {
                return;
            }
        }
        String nextMark = appendMarkToken(smartMark, "模糊");
        record.put("SmartMark", nextMark);
        if (record.containsKey("Mark") && record.getString("Mark") != null && !record.getString("Mark").trim().isEmpty()) {
            record.put("Mark", appendMarkToken(record.getString("Mark"), "模糊"));
        }
    }

    private static String appendMarkToken(String existing, String token) {
        if (existing == null || existing.trim().isEmpty() || "-".equals(existing.trim()) || "正常".equals(existing.trim())) {
            return token;
        }
        if (existing.contains(token)) {
            return existing;
        }
        return existing + ";" + token;
    }

    public static String sanitizeOptionalText(String value) {
        return isUnrecognized(value) ? "" : value.trim();
    }

    private static boolean isTimeFieldKey(String key) {
        if (key == null) {
            return false;
        }
        for (String timeKey : TIME_FIELD_KEYS) {
            if (timeKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTimeFieldNonTimeLabel(String key, String raw) {
        return isTimeFieldKey(key) && RecognizedTimeNormalizer.isNonTimeFieldLabel(raw);
    }

    /** 仓库仅保留图片中识别到的值，未识别则留空。 */
    public static void sanitizeWarehouse(JSONObject record) {
        if (record == null) {
            return;
        }
        record.put("Entrepot", sanitizeOptionalText(record.getString("Entrepot")));
    }
}
