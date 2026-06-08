package com.attendance.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 根据 SIGNATURE 是否识别到有效内容，生成员工签字结果（未签字 / 已签字）。
 */
public final class SignatureMarkResolver {

    public static final String SIGNED = "已签字";
    public static final String UNSIGNED = "未签字";

    /** @deprecated 历史三档标记，读取时映射为 {@link #SIGNED} */
    @Deprecated
    public static final String SIGNED_CONFIRMED = "已签字确认";
    /** @deprecated 历史三档标记，读取时映射为 {@link #UNSIGNED} */
    @Deprecated
    public static final String UNSIGNED_CONFIRMED = "未签字确认";

    /** 签名列表头关键词（表头可含其它说明文字，用于定位列） */
    private static final String[] SIGNATURE_COLUMN_HEADER_KEYWORDS = {
            "员工签名", "signature", "signatura", "firma", "员工签", "签名",
            "employee signature", "handtekening", "unterschrift"
    };

    private static final Set<String> SIGNATURE_COLUMN_HEADERS = new HashSet<>(Arrays.asList(
            SIGNATURE_COLUMN_HEADER_KEYWORDS
    ));

    private static final Set<String> BLANK_SIGNATURE_TOKENS = new HashSet<>(Arrays.asList(
            "n/a", "na", "none", "null"
    ));

    /** 有笔迹但无法转写或模糊时，应视为已签字 */
    private static final Set<String> ILLEGIBLE_SIGNATURE_TOKENS = new HashSet<>(Arrays.asList(
            "???", "??", "unknown", "illegible",
            "模糊", "不清楚", "borroso", "wazig", "rozmazan", "unscharf", "flou", "borrosa"
    ));

    private SignatureMarkResolver() {
    }

    public static boolean isSignatureMarkToken(String mark) {
        if (mark == null) {
            return false;
        }
        String m = mark.trim();
        return SIGNED.equals(m)
                || UNSIGNED.equals(m)
                || SIGNED_CONFIRMED.equals(m)
                || UNSIGNED_CONFIRMED.equals(m);
    }

    /**
     * 表头单元格是否为员工签字列（允许含其它说明文字，如 Firma del dipendente）。
     */
    public static boolean isSignatureColumnHeaderText(String headerText) {
        if (headerText == null || headerText.trim().isEmpty()) {
            return false;
        }
        String lower = headerText.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("firma e conferma") || lower.contains("responsabile")) {
            return false;
        }
        for (String keyword : SIGNATURE_COLUMN_HEADER_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 旧数据展示规范化：三档或手写原文统一映射为「未签字 / 已签字」。
     */
    public static String normalizeLegacySignature(String signature) {
        return normalizeLegacySignature(signature, false);
    }

    public static String normalizeLegacySignature(String signature, boolean rowDeleted) {
        if (rowDeleted) {
            return UNSIGNED;
        }
        if (isBlankSignature(signature)) {
            return UNSIGNED;
        }
        String trimmed = signature.trim();
        if (UNSIGNED_CONFIRMED.equals(trimmed) || UNSIGNED.equals(trimmed)) {
            return UNSIGNED;
        }
        return SIGNED;
    }

    /** AI 若误输出签名列表头字面量，视为空白单元格。 */
    public static String sanitizeAiSignature(String signature) {
        if (signature == null) {
            return "";
        }
        String trimmed = signature.trim();
        if (trimmed.isEmpty() || isSignatureHeaderEcho(trimmed)) {
            return "";
        }
        return trimmed;
    }

    public static boolean isSignatureColumnHeader(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return SIGNATURE_COLUMN_HEADERS.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    /** 数据行误填表头或表头说明文字（非真实签字内容）。 */
    public static boolean isSignatureHeaderEcho(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        if (isSignatureColumnHeader(value)) {
            return true;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("firma e conferma") || lower.contains("responsabile")) {
            return true;
        }
        if (isSignatureColumnHeaderText(value) && lower.length() <= 60) {
            return true;
        }
        return false;
    }

    public static boolean isSignatureStruckOut(String rawSignature) {
        if (rawSignature == null || rawSignature.trim().isEmpty()) {
            return false;
        }
        String lower = rawSignature.trim().toLowerCase(Locale.ROOT);
        return lower.contains("划线")
                || lower.contains("划掉")
                || "划线删除".equals(rawSignature.trim())
                || lower.contains("barré")
                || lower.contains("barrato")
                || lower.contains("crossed")
                || lower.contains("strikethrough")
                || lower.contains("cancellato");
    }

    /**
     * @return 已签字 | 未签字
     */
    public static String resolve(String signature) {
        return resolve(signature, false);
    }

    public static String resolve(String signature, boolean rowDeleted) {
        if (rowDeleted) {
            return UNSIGNED;
        }
        return isBlankSignature(signature) ? UNSIGNED : SIGNED;
    }

    /**
     * 根据 AI 原始输出与行上下文生成签字结果。
     */
    public static String resolveFromAiOutput(
            String rawAiSignature,
            boolean rowDeleted,
            String smartMark,
            String arrivee,
            String depart,
            String mark) {
        if (rowDeleted || isRowDeletedForSignature(false, smartMark)) {
            return UNSIGNED;
        }
        if (isSignatureStruckOut(rawAiSignature)) {
            return UNSIGNED;
        }
        String sanitized = sanitizeAiSignature(rawAiSignature);
        if (!sanitized.isEmpty()) {
            return resolve(sanitized);
        }
        if (shouldInferSignedWhenEmpty(arrivee, depart, mark, smartMark)) {
            return SIGNED;
        }
        return UNSIGNED;
    }

    /** 兼容旧调用方，忽略姓名参数。 */
    public static String resolve(String signature, String employeeName) {
        return resolve(signature);
    }

    public static boolean isRowDeletedForSignature(boolean isDeleted, String smartMark) {
        if (isDeleted) {
            return true;
        }
        if (smartMark == null || smartMark.trim().isEmpty()) {
            return false;
        }
        for (String part : smartMark.split("[;；,，]")) {
            if ("已删除".equals(part == null ? "" : part.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldInferSignedWhenEmpty(
            String arrivee, String depart, String mark, String smartMark) {
        if (containsAbsentMark(mark) || containsAbsentMark(smartMark)) {
            return false;
        }
        return hasFilledTime(arrivee) || hasFilledTime(depart);
    }

    public static boolean isBlankSignature(String signature) {
        if (signature == null) {
            return true;
        }
        String trimmed = signature.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (isSignatureMarkToken(trimmed)) {
            return UNSIGNED.equals(trimmed);
        }
        if (isIllegibleSignature(trimmed)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (BLANK_SIGNATURE_TOKENS.contains(lower)) {
            return true;
        }
        return isSignatureHeaderEcho(trimmed);
    }

    public static boolean isIllegibleSignature(String signature) {
        if (signature == null) {
            return false;
        }
        String trimmed = signature.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return ILLEGIBLE_SIGNATURE_TOKENS.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    private static boolean containsAbsentMark(String mark) {
        if (mark == null || mark.trim().isEmpty()) {
            return false;
        }
        for (String part : mark.split("[;；,，]")) {
            if ("未出勤".equals(part == null ? "" : part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFilledTime(String time) {
        if (time == null) {
            return false;
        }
        String trimmed = time.trim();
        if (trimmed.isEmpty() || "???".equals(trimmed) || "illegible".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return trimmed.matches("\\d{1,2}:\\d{2}");
    }
}
