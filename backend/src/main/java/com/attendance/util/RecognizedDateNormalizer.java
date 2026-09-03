package com.attendance.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日期归一化 → YYYY-MM-DD。
 * a/b/yyyy（或 . -）：默认按月/日/年；月不能 &gt;12，若一侧 &gt;12 则该侧为日、另一侧为月。
 * 例：12/06/2026→2026-12-06；13/06/2026→2026-06-13；06/13/2026→2026-06-13。
 */
public final class RecognizedDateNormalizer {

    private static final Pattern CANONICAL = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})$");
    private static final Pattern MDY4 = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
    private static final Pattern MDY2 = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2})$");

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

        Matcher mdy4 = MDY4.matcher(str);
        if (mdy4.matches()) {
            String built = resolveMonthDayYear(mdy4.group(1), mdy4.group(2), Integer.parseInt(mdy4.group(3)));
            return built != null ? built : str;
        }

        Matcher mdy2 = MDY2.matcher(str);
        if (mdy2.matches()) {
            String built = resolveMonthDayYear(mdy2.group(1), mdy2.group(2), 2000 + Integer.parseInt(mdy2.group(3)));
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

    /** a/b/year：默认月日；月不能&gt;12 时互换（&gt;12 的一侧为日） */
    private static String resolveMonthDayYear(String a, String b, int year) {
        int p = Integer.parseInt(a);
        int q = Integer.parseInt(b);
        if (p > 12 && q >= 1 && q <= 12) {
            return buildCanonical(String.valueOf(year), q, p);
        }
        if (q > 12 && p >= 1 && p <= 12) {
            return buildCanonical(String.valueOf(year), p, q);
        }
        if (p >= 1 && p <= 12 && q >= 1) {
            return buildCanonical(String.valueOf(year), p, q);
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
