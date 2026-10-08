package com.attendance.util;

import java.util.Map;

import com.alibaba.fastjson.JSONObject;

/**
 * 识别文本字段清洗：工号（数字+字母）、姓名（保留有内容括号）、仓库/中介。
 */
public final class RecognizedTextNormalizer {

    private static final String BRACKET_QUOTE = "[\\[\\]{}\"'【】「」]";
    private static final String LABEL_PAREN = "[()（）]";

    private RecognizedTextNormalizer() {
    }

    public static String normalizeWorkerNo(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(text)) {
            return "";
        }
        return text.replaceAll(BRACKET_QUOTE, "").replaceAll("[^a-zA-Z0-9]", "");
    }

    public static String normalizePersonName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(s)) {
            return "";
        }
        s = s.replaceAll(BRACKET_QUOTE, "");
        s = s.replaceAll("\\(\\s*\\)", "");
        s = s.replaceAll("（\\s*）", "");
        s = s.replaceAll("（\\s*\\)", "");
        s = s.replaceAll("\\(\\s*）", "");
        return collapseSpaces(s);
    }

    public static String normalizeLabelText(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(s)) {
            return "";
        }
        s = s.replaceAll(BRACKET_QUOTE, "").replaceAll(LABEL_PAREN, "");
        return collapseSpaces(s);
    }

    public static void normalizeRecordTextFields(JSONObject record) {
        if (record == null) {
            return;
        }
        if (record.containsKey("NO")) {
            record.put("NO", normalizeWorkerNo(record.getString("NO")));
        }
        if (record.containsKey("NOM_PRENOM")) {
            record.put("NOM_PRENOM", normalizePersonName(record.getString("NOM_PRENOM")));
        }
        if (record.containsKey("Name")) {
            record.put("Name", normalizePersonName(record.getString("Name")));
        }
        if (record.containsKey("Entrepot")) {
            record.put("Entrepot", normalizeLabelText(record.getString("Entrepot")));
        }
        if (record.containsKey("AGENCE_INTERIMAIRE")) {
            record.put("AGENCE_INTERIMAIRE", normalizeLabelText(record.getString("AGENCE_INTERIMAIRE")));
        }
    }

    /** 确认提交前对用户编辑字段做文本与日期归一化。 */
    public static void normalizeRecordFields(Map<String, Object> record) {
        if (record == null) {
            return;
        }
        if (record.containsKey("NO")) {
            record.put("NO", normalizeWorkerNo(String.valueOf(record.get("NO"))));
        }
        if (record.containsKey("NOM_PRENOM")) {
            record.put("NOM_PRENOM", normalizePersonName(String.valueOf(record.get("NOM_PRENOM"))));
        }
        if (record.containsKey("Name")) {
            record.put("Name", normalizePersonName(String.valueOf(record.get("Name"))));
        }
        if (record.containsKey("Entrepot")) {
            record.put("Entrepot", normalizeLabelText(String.valueOf(record.get("Entrepot"))));
        }
        if (record.containsKey("AGENCE_INTERIMAIRE")) {
            record.put("AGENCE_INTERIMAIRE", normalizeLabelText(String.valueOf(record.get("AGENCE_INTERIMAIRE"))));
        }
        if (record.containsKey("Date")) {
            // 草稿/确认路径：用户编辑后的 Date 为准。识别阶段已用 DATE_RAW 重装过一次；
            // 此处若再 applyDateWithRaw，会把人工改期（含 DatePicker）覆盖回 OCR 原始段。
            String dateValue = String.valueOf(record.get("Date"));
            String normalized = RecognizedDateNormalizer.normalizeDate(dateValue);
            if (RecognizedDateNormalizer.isValidCanonicalDate(normalized)) {
                record.put("Date", normalized);
                Object dateRawObj = record.get("DATE_RAW");
                if (dateRawObj != null && !String.valueOf(dateRawObj).trim().isEmpty()) {
                    String fromRaw = RecognizedDateNormalizer.applyDateWithRaw(dateValue, String.valueOf(dateRawObj));
                    if (!normalized.equals(fromRaw)) {
                        record.put("DATE_RAW", "");
                    }
                }
            } else {
                String dateRaw = record.get("DATE_RAW") == null ? "" : String.valueOf(record.get("DATE_RAW"));
                record.put("Date", RecognizedDateNormalizer.applyDateWithRaw(dateValue, dateRaw));
            }
        }
        if (record.containsKey("WorkDate")) {
            record.put("WorkDate", RecognizedDateNormalizer.normalizeDate(String.valueOf(record.get("WorkDate"))));
        }
    }

    private static String collapseSpaces(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
