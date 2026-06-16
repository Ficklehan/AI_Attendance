package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 识别结果行级可读性判定（与 shared/js/recognitionMarkCore 词表对齐）。
 */
public final class RowReadabilitySupport {

    private static final List<String> ABSENT_MARK_TOKENS = Arrays.asList(
            "未出勤", "absent", "ausente", "abwesend", "afwezig", "nieobecny", "nepřítom"
    );

    private static final Map<String, String[]> FIELD_ALIASES = new HashMap<>();

    static {
        FIELD_ALIASES.put("NOM_PRENOM", new String[]{"NOM_PRENOM", "Name"});
        FIELD_ALIASES.put("Date", new String[]{"Date", "WorkDate"});
        FIELD_ALIASES.put("DEPAR", new String[]{"DEPAR", "DEPART"});
    }

    private RowReadabilitySupport() {
    }

    public static String getFieldValue(JSONObject record, String fieldKey) {
        if (record == null || fieldKey == null) {
            return "";
        }
        String[] aliases = FIELD_ALIASES.get(fieldKey);
        if (aliases != null) {
            for (String alias : aliases) {
                String value = safe(record.getString(alias));
                if (!value.isEmpty()) {
                    return value;
                }
            }
            return "";
        }
        return safe(record.getString(fieldKey));
    }

    /**
     * 单元格无数据（空白），不计入「字段看不清」统计。
     */
    public static boolean isBlankField(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 模型明确标记为无法辨认（??? / illegible 等），用于「字段看不清」统计。
     * 空白单元格不算看不清。
     */
    public static boolean isExplicitlyUnreadableField(String value) {
        if (isBlankField(value)) {
            return false;
        }
        String t = value.trim();
        return "???".equals(t)
                || "??".equals(t)
                || "unknown".equalsIgnoreCase(t)
                || "illegible".equalsIgnoreCase(t);
    }

    /** 识别结果元数据或单元格值标记为看不清（含 sanitize 后的 {@code _unreadableFields}）。 */
    public static boolean isFieldUnreadable(JSONObject record, String fieldKey) {
        if (record == null || fieldKey == null || fieldKey.trim().isEmpty()) {
            return false;
        }
        if (isFieldListedUnreadable(record, fieldKey)) {
            return true;
        }
        return isExplicitlyUnreadableField(getFieldValue(record, fieldKey));
    }

    private static boolean isFieldListedUnreadable(JSONObject record, String fieldKey) {
        JSONArray unreadable = record.getJSONArray(RecognizedFieldSanitizer.UNREADABLE_FIELDS_KEY);
        if (unreadable == null || unreadable.isEmpty()) {
            return false;
        }
        String target = fieldKey.trim();
        for (int i = 0; i < unreadable.size(); i++) {
            Object item = unreadable.get(i);
            if (item != null && target.equalsIgnoreCase(String.valueOf(item).trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 身份/可读性判断：空白或无法辨认均视为 unknown（用于有效行、未出勤等行级逻辑）。
     */
    public static boolean isUnknown(String value) {
        if (isBlankField(value)) {
            return true;
        }
        return isExplicitlyUnreadableField(value);
    }

    public static boolean isEffectiveRow(JSONObject record) {
        if (record == null) {
            return false;
        }
        return !isUnknown(safe(record.getString("NO")))
                || !isUnknown(safe(record.getString("NOM_PRENOM")));
    }

    public static boolean isDeletedRow(JSONObject record) {
        return record != null && Boolean.TRUE.equals(record.getBoolean("isDeleted"));
    }

    public static boolean isAbsentRow(JSONObject record) {
        if (record == null) {
            return false;
        }
        String smartMark = safe(record.getString("SmartMark"));
        if (containsAnyToken(smartMark, ABSENT_MARK_TOKENS)) {
            return true;
        }
        String mark = safe(record.getString("Mark"));
        if (containsAnyToken(mark, ABSENT_MARK_TOKENS)) {
            return true;
        }
        String arrive = safe(record.getString("ARRIVEE"));
        String depart = safe(record.getString("DEPAR"));
        boolean timesEmpty = isUnknown(arrive) && isUnknown(depart);
        boolean identityEmpty = isUnknown(safe(record.getString("NO")))
                && isUnknown(safe(record.getString("NOM_PRENOM")));
        return timesEmpty && identityEmpty;
    }

    /**
     * 低可读行：配置的必填字段中任一为 ??? / illegible 等；排除已删除、未出勤。
     * 仅依据必填字段，不看工号等其他列，也不看 SmartMark 模糊标记。
     */
    public static boolean isLowReadabilityRow(JSONObject record, List<String> requiredFields) {
        if (record == null || isDeletedRow(record) || isAbsentRow(record)) {
            return false;
        }
        if (requiredFields == null || requiredFields.isEmpty()) {
            return false;
        }
        for (String field : requiredFields) {
            if (isFieldUnreadable(record, field)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> safeRequiredFields(List<String> requiredFields) {
        if (requiredFields == null || requiredFields.isEmpty()) {
            return Collections.emptyList();
        }
        return requiredFields;
    }

    public static boolean containsAnyToken(String text, List<String> tokens) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String part : text.split("[;；,，]")) {
            String piece = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (piece.isEmpty()) {
                continue;
            }
            for (String token : tokens) {
                if (piece.contains(token.toLowerCase(Locale.ROOT)) || lower.contains(token.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
