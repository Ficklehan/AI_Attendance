package com.attendance.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 根据 SIGNATURE 与姓名列匹配度，生成签字相关 SmartMark 标记。
 */
public final class SignatureMarkResolver {

    public static final String SIGNED_CONFIRMED = "已签字确认";
    public static final String UNSIGNED_CONFIRMED = "未签字确认";
    public static final String SIGNED = "已签字";

    private static final double MATCH_THRESHOLD = 0.70;

    private static final Set<String> BLANK_SIGNATURE_TOKENS = new HashSet<>(Arrays.asList(
            "", "???", "??", "unknown", "illegible", "n/a", "na", "none", "null",
            "员工签名", "signature", "signatura", "firma", "员工签", "签名",
            "sign", "signed", "unsigned"
    ));

    private SignatureMarkResolver() {
    }

    public static boolean isSignatureMarkToken(String mark) {
        if (mark == null) {
            return false;
        }
        String m = mark.trim();
        return SIGNED_CONFIRMED.equals(m) || UNSIGNED_CONFIRMED.equals(m) || SIGNED.equals(m);
    }

    /**
     * 旧数据展示规范化：已是三档标记则保留；空白为未签字确认；其余手写原文默认已签字确认（不再展示原文）。
     */
    public static String normalizeLegacySignature(String signature) {
        if (isSignatureMarkToken(signature)) {
            return signature.trim();
        }
        if (isBlankSignature(signature)) {
            return UNSIGNED_CONFIRMED;
        }
        return SIGNED_CONFIRMED;
    }

    /**
     * @return 已签字确认 | 未签字确认 | 已签字
     */
    public static String resolve(String signature, String employeeName) {
        if (isBlankSignature(signature)) {
            return UNSIGNED_CONFIRMED;
        }
        double ratio = matchRatio(signature, employeeName);
        if (ratio >= MATCH_THRESHOLD) {
            return SIGNED_CONFIRMED;
        }
        return SIGNED;
    }

    public static boolean isBlankSignature(String signature) {
        if (signature == null) {
            return true;
        }
        String trimmed = signature.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (BLANK_SIGNATURE_TOKENS.contains(lower)) {
            return true;
        }
        if (lower.equals("signature") || lower.equals("员工签名")) {
            return true;
        }
        return false;
    }

    public static double matchRatio(String signature, String employeeName) {
        if (isBlankSignature(signature) || isBlankName(employeeName)) {
            return 0.0;
        }
        String sig = normalize(signature);
        String name = normalize(employeeName);
        if (sig.isEmpty() || name.isEmpty()) {
            return 0.0;
        }

        double best = similarity(sig, name);

        String[] tokens = name.split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 2) {
                best = Math.max(best, similarity(sig, token));
            }
        }
        if (tokens.length >= 2) {
            best = Math.max(best, similarity(sig, tokens[0]));
            best = Math.max(best, similarity(sig, tokens[tokens.length - 1]));
            best = Math.max(best, similarity(sig, tokens[0] + tokens[tokens.length - 1]));
        }

        if (sig.contains(name) || name.contains(sig)) {
            int shorter = Math.min(sig.length(), name.length());
            int longer = Math.max(sig.length(), name.length());
            if (longer > 0) {
                best = Math.max(best, (double) shorter / longer);
            }
        }

        return best;
    }

    private static boolean isBlankName(String name) {
        if (name == null) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty()
                || "???".equals(trimmed)
                || "??".equals(trimmed)
                || "illegible".equalsIgnoreCase(trimmed)
                || "unknown".equalsIgnoreCase(trimmed);
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String folded = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String upper = folded.toUpperCase(Locale.ROOT);
        return upper.replaceAll("[^A-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0 - ((double) distance / maxLen);
    }

    static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
