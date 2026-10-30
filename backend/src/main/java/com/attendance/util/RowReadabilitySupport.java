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

    private static final List<String> BLUR_MARK_TOKENS = Arrays.asList(
            "模糊", "blur", "borroso", "unscharf", "wazig", "rozmazan", "nieostry"
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

    /**
     * 印刷空行：姓名格确认空白（无笔迹），且到达/离开都空。
     * 有姓名、或姓名格有笔迹但看不清（???）的行都要保留；??? 是模糊，不是「没有姓名」。
     */
    public static boolean isNamelessEmptyClockRow(JSONObject record) {
        if (record == null) {
            return false;
        }
        if (Boolean.TRUE.equals(record.getBoolean("_manuallyAdded"))) {
            return false;
        }
        // 姓名格有笔迹但看不清：sanitize 后值为空，但会记入 _unreadableFields / 残留 ???。这类行要保留。
        if (isFieldUnreadable(record, "NOM_PRENOM")
                || RecognizedFieldSanitizer.isExplicitUnreadable(getFieldValue(record, "NOM_PRENOM"))) {
            return false;
        }
        return isPrintedBlankName(getFieldValue(record, "NOM_PRENOM"))
                && RecognizedFieldSanitizer.isUnrecognized(getFieldValue(record, "ARRIVEE"))
                && RecognizedFieldSanitizer.isUnrecognized(getFieldValue(record, "DEPAR"));
    }

    /** 印刷占位/空姓名，不含模型用问号表示的看不清。 */
    static boolean isPrintedBlankName(String name) {
        if (name == null) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) || "—".equals(trimmed);
    }

    public static boolean isDeletedRow(JSONObject record) {
        return record != null && Boolean.TRUE.equals(record.getBoolean("isDeleted"));
    }

    public static boolean isAbsentRow(JSONObject record) {
        return RecognitionStatusMark.isAbsentStatus(record);
    }

    /**
     * 模型 JSON 落库前：备注含 ABSENT / 标记含未出勤时的收口。
     * <ul>
     *   <li>本格备注 ABSENT 且签名格无笔迹：标记未出勤，但<strong>不清空</strong>模型返回的到离/休息。</li>
     *   <li>本格已有合法到离且签名格有真实转写：视为出勤；漏进来的纯 ABSENT 备注清掉。</li>
     * </ul>
     * 模型字段只允许页面人工修改；Java 禁止把 ARRIVEE/DEPAR/PAUSE 置空。
     *
     * @return true 表示本行已按未出勤规范化（仅标记，不清格子）
     */
    public static boolean applyAbsentObservationRule(JSONObject record) {
        if (record == null || isDeletedRow(record)) {
            return false;
        }
        if (Boolean.TRUE.equals(record.getBoolean("_manuallyAdded"))) {
            return false;
        }
        boolean fromObservations = observationsIndicateAbsent(record);
        if (fromObservations && !hasPixelSignature(record) && !hasBlurMark(record)) {
            markAbsentKeepModelClocks(record);
            return true;
        }
        if (hasRecognizedPunchTime(record) || hasBlurMark(record)) {
            if (isPureAbsentObservation(record.getString("Observations"))
                    && (hasPixelSignature(record) || hasBlurMark(record))) {
                record.put("Observations", "");
            }
            return false;
        }
        String mark = safe(record.getString("Mark"));
        boolean fromMark = containsAnyToken(mark, ABSENT_MARK_TOKENS);
        if (!fromMark && !fromObservations) {
            return false;
        }
        markAbsentKeepModelClocks(record);
        return true;
    }

    /**
     * 历史：曾清掉「疑似抄自班次/邻行」的到离。
     * 现策略：Java <strong>不得</strong>清空模型返回的 ARRIVEE/DEPAR/PAUSE；
     * 需要改数只走页面人工编辑。本方法保留调用点，恒返回 0。
     */
    public static int applyCopiedClockWipe(List<JSONObject> records) {
        return 0;
    }

    /** @deprecated 禁止清空模型到离；保留供旧测试/诊断引用，恒为 false。 */
    static boolean shouldWipeCopiedClocks(JSONObject record, JSONObject prev, JSONObject next) {
        return false;
    }

    static boolean isBlankRawSignature(JSONObject record) {
        if (record == null) {
            return true;
        }
        return SignatureMarkResolver.isBlankSignature(record.getString("SIGNATURE"));
    }

    static boolean hasPixelSignature(JSONObject record) {
        if (record == null) {
            return false;
        }
        String raw = record.getString("SIGNATURE_RAW");
        if (raw != null && !raw.trim().isEmpty()) {
            return SignatureMarkResolver.hasPixelSignature(raw);
        }
        return SignatureMarkResolver.hasPixelSignature(record.getString("SIGNATURE"));
    }

    /**
     * 标记未出勤，但保留模型返回的 ARRIVEE/DEPAR/PAUSE（禁止 Java 清空模型 JSON）。
     */
    static void markAbsentKeepModelClocks(JSONObject record) {
        if (record == null) {
            return;
        }
        record.put("Mark", "未出勤");
    }

    /**
     * @deprecated 禁止清空模型到离/休息。请用 {@link #markAbsentKeepModelClocks(JSONObject)}。
     */
    static void clearCopiedClocks(JSONObject record) {
        markAbsentKeepModelClocks(record);
    }

    static boolean clocksMatchOwnShift(JSONObject record) {
        String[] pair = clockPair(record);
        String[] shift = RecognizedTimeNormalizer.splitShiftStartEnd(
                getFieldValue(record, "HORAIRES_DU_TRAVAIL"));
        if (pair == null || shift == null) {
            return false;
        }
        return pair[0].equals(shift[0]) && pair[1].equals(shift[1]);
    }

    static boolean clocksMatch(JSONObject left, JSONObject right) {
        String[] a = clockPair(left);
        String[] b = clockPair(right);
        if (a == null || b == null) {
            return false;
        }
        return a[0].equals(b[0]) && a[1].equals(b[1]);
    }

    static String[] clockPair(JSONObject record) {
        if (record == null) {
            return null;
        }
        String arrive = RecognizedTimeNormalizer.normalizeClockTime(getFieldValue(record, "ARRIVEE"));
        String depart = RecognizedTimeNormalizer.normalizeClockTime(getFieldValue(record, "DEPAR"));
        if (!RecognizedTimeNormalizer.isValidClockTime(arrive)
                || !RecognizedTimeNormalizer.isValidClockTime(depart)) {
            return null;
        }
        return new String[]{arrive, depart};
    }

    static JSONObject neighborInSheet(List<JSONObject> records, int index, int direction) {
        if (records == null || index < 0 || index >= records.size() || direction == 0) {
            return null;
        }
        JSONObject current = records.get(index);
        String date = sheetDateKey(current);
        String warehouse = sheetWarehouseKey(current);
        int j = index + direction;
        while (j >= 0 && j < records.size()) {
            JSONObject candidate = records.get(j);
            if (!isDeletedRow(candidate)
                    && date.equals(sheetDateKey(candidate))
                    && warehouse.equals(sheetWarehouseKey(candidate))) {
                return candidate;
            }
            j += direction;
        }
        return null;
    }

    private static String sheetDateKey(JSONObject record) {
        return safe(getFieldValue(record, "Date"));
    }

    private static String sheetWarehouseKey(JSONObject record) {
        return safe(getFieldValue(record, "Entrepot")).toLowerCase(Locale.ROOT);
    }

    /**
     * 模糊与未出勤互斥：模型因格内有笔迹看不清而标了模糊时，去掉未出勤，不得两标并存。
     */
    public static void dropAbsentIfBlurred(JSONObject record) {
        if (record == null || !hasBlurMark(record)) {
            return;
        }
        record.put("Mark", removeAbsentTokens(record.getString("Mark")));
        record.put("SmartMark", removeAbsentTokens(record.getString("SmartMark")));
    }

    /** 模型因看不清标了模糊：不得改判未出勤，也不得用邻行 ABSENT 覆盖。 */
    public static boolean hasBlurMark(JSONObject record) {
        if (record == null) {
            return false;
        }
        return containsAnyToken(safe(record.getString("Mark")), BLUR_MARK_TOKENS)
                || containsAnyToken(safe(record.getString("SmartMark")), BLUR_MARK_TOKENS);
    }

    /** 到达或离开已识别出合法 HH:MM。 */
    public static boolean hasRecognizedPunchTime(JSONObject record) {
        if (record == null) {
            return false;
        }
        return isValidClock(getFieldValue(record, "ARRIVEE"))
                || isValidClock(getFieldValue(record, "DEPAR"));
    }

    static boolean isPureAbsentObservation(String observations) {
        String text = safe(observations);
        if (text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : ABSENT_MARK_TOKENS) {
            if (lower.equals(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidClock(String raw) {
        String clock = RecognizedTimeNormalizer.normalizeClockTime(raw);
        return clock != null && clock.matches("\\d{2}:\\d{2}");
    }

    /** 备注列是否含 ABSENT / absent 等缺勤词（与标记词表对齐，大小写不敏感）。 */
    public static boolean observationsIndicateAbsent(JSONObject record) {
        if (record == null) {
            return false;
        }
        String observations = safe(record.getString("Observations"));
        if (observations.isEmpty()) {
            return false;
        }
        return containsAnyToken(observations, ABSENT_MARK_TOKENS);
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

    static String removeAbsentTokens(String mark) {
        if (mark == null || mark.trim().isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String part : mark.split("[;；,，]")) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (containsAnyToken(token, ABSENT_MARK_TOKENS)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(';');
            }
            kept.append(token);
        }
        return kept.toString();
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
