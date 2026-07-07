package com.attendance.util;

import com.attendance.config.CountryCatalog;

/**
 * 中介账单等场景：将库内检索键/代码转为可读展示名。
 */
public final class BillingDisplaySupport {

    private BillingDisplaySupport() {
    }

    public static String resolveCountryLabel(String display, String key) {
        String fromDisplay = resolveCountryFromValue(display);
        if (fromDisplay != null) {
            return fromDisplay;
        }
        String fromKey = resolveCountryFromValue(key);
        if (fromKey != null) {
            return fromKey;
        }
        return firstNonBlank(display, key);
    }

    public static String resolveTextLabel(String display, String key) {
        String text = trimToNull(display);
        String normalizedKey = trimToNull(key);
        if (text != null) {
            if (normalizedKey != null && text.equalsIgnoreCase(normalizedKey)) {
                return humanizeKey(normalizedKey);
            }
            if (looksLikeNormalizedKey(text) && normalizedKey != null
                    && text.equalsIgnoreCase(normalizedKey)) {
                return humanizeKey(normalizedKey);
            }
            return text;
        }
        if (normalizedKey != null) {
            return humanizeKey(normalizedKey);
        }
        return "";
    }

    public static String mergeCountryLabel(String current, String currentKey, String display, String key) {
        return pickBetter(current, resolveCountryLabel(display, key), currentKey, key);
    }

    public static String mergeTextLabel(String current, String currentKey, String display, String key) {
        return pickBetter(current, resolveTextLabel(display, key), currentKey, key);
    }

    private static String resolveCountryFromValue(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        String code = CountryCatalog.resolveCountryCodeFromPays(trimmed);
        if (code == null && CountryCatalog.isSupported(trimmed)) {
            code = CountryResolver.normalize(trimmed);
        }
        if (code != null) {
            String catalog = CountryCatalog.defaultPaysLabel(code);
            if (catalog != null && !catalog.trim().isEmpty()) {
                return catalog.trim();
            }
            return code;
        }
        if (!trimmed.equals(trimmed.toUpperCase()) || trimmed.length() > 3) {
            return trimmed;
        }
        return null;
    }

    private static String pickBetter(String current, String candidate, String currentKey, String key) {
        if (isBlank(candidate)) {
            return current != null ? current : "";
        }
        if (isBlank(current)) {
            return candidate;
        }
        if (candidate.length() > current.length()) {
            return candidate;
        }
        if (isMostlyUppercase(current) && !isMostlyUppercase(candidate)) {
            return candidate;
        }
        if (current.equalsIgnoreCase(safe(currentKey)) && !candidate.equalsIgnoreCase(safe(key))) {
            return candidate;
        }
        return current;
    }

    static String humanizeKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "";
        }
        String trimmed = key.trim();
        if (trimmed.length() <= 4 && trimmed.equals(trimmed.toUpperCase())) {
            return trimmed;
        }
        String[] parts = trimmed.split("[\\s_\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (part.contains("&")) {
                String[] sub = part.split("&", -1);
                for (int i = 0; i < sub.length; i++) {
                    if (i > 0) {
                        sb.append('&');
                    }
                    sb.append(capitalizeToken(sub[i]));
                }
            } else {
                sb.append(capitalizeToken(part));
            }
        }
        return sb.toString();
    }

    private static String capitalizeToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (token.length() <= 4 && token.equals(token.toUpperCase())) {
            return token;
        }
        return token.substring(0, 1).toUpperCase() + token.substring(1).toLowerCase();
    }

    private static boolean looksLikeNormalizedKey(String value) {
        String trimmed = value.trim();
        return trimmed.equals(trimmed.toUpperCase()) && trimmed.length() <= 64;
    }

    private static boolean isMostlyUppercase(String value) {
        if (isBlank(value)) {
            return false;
        }
        int upper = 0;
        int letters = 0;
        for (char ch : value.toCharArray()) {
            if (Character.isLetter(ch)) {
                letters++;
                if (Character.isUpperCase(ch)) {
                    upper++;
                }
            }
        }
        return letters > 0 && upper * 100 / letters >= 80;
    }

    private static String firstNonBlank(String a, String b) {
        String ta = trimToNull(a);
        if (ta != null) {
            return ta;
        }
        String tb = trimToNull(b);
        return tb != null ? tb : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
