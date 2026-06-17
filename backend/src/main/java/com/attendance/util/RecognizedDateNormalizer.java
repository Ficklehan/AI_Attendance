package com.attendance.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日期归一化：明确格式 → YYYY-MM-DD；歧义如 03/04/2026 保留原样。
 */
public final class RecognizedDateNormalizer {

    private static final Pattern CANONICAL = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})$");
    private static final Pattern DMY4 = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
    private static final Pattern DMY2 = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2})$");

    private RecognizedDateNormalizer() {
    }

    public static String normalizeDate(String raw) {
        if (raw == null) {
            return "";
        }
        String str = raw.trim();
        if (str.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(str)) {
            return "";
        }
        if (isValidCanonicalDate(str)) {
            return str;
        }

        Matcher ymd = YMD.matcher(str);
        if (ymd.matches()) {
            String built = buildCanonical(ymd.group(1),
                    Integer.parseInt(ymd.group(2)),
                    Integer.parseInt(ymd.group(3)));
            return built != null ? built : str;
        }

        Matcher dmy4 = DMY4.matcher(str);
        if (dmy4.matches()) {
            String built = tryDmyOrMdy(dmy4.group(1), dmy4.group(2), Integer.parseInt(dmy4.group(3)));
            return built != null ? built : str;
        }

        Matcher dmy2 = DMY2.matcher(str);
        if (dmy2.matches()) {
            String built = tryDmyOrMdy(dmy2.group(1), dmy2.group(2), 2000 + Integer.parseInt(dmy2.group(3)));
            return built != null ? built : str;
        }

        return str;
    }

    public static boolean isValidCanonicalDate(String value) {
        if (value == null) {
            return false;
        }
        Matcher m = CANONICAL.matcher(value.trim());
        if (!m.matches()) {
            return false;
        }
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        if (month < 1 || month > 12) {
            return false;
        }
        if (day < 1 || day > daysInMonth(year, month)) {
            return false;
        }
        return true;
    }

    public static boolean isDateFormatInvalid(String value) {
        if (value == null || value.trim().isEmpty() || RecognizedFieldSanitizer.isUnrecognized(value)) {
            return false;
        }
        return !isValidCanonicalDate(value.trim());
    }

    private static String tryDmyOrMdy(String a, String b, int year) {
        int p = Integer.parseInt(a);
        int q = Integer.parseInt(b);
        if (p > 12 && q <= 12) {
            return buildCanonical(String.valueOf(year), q, p);
        }
        if (q > 12 && p <= 12) {
            return buildCanonical(String.valueOf(year), p, q);
        }
        if (p <= 12 && q <= 12) {
            return null;
        }
        return null;
    }

    private static String buildCanonical(String year, int month, int day) {
        String candidate = String.format("%s-%02d-%02d", year, month, day);
        return isValidCanonicalDate(candidate) ? candidate : null;
    }

    private static int daysInMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }
}
