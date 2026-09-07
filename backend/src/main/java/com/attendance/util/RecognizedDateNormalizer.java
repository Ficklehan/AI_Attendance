package com.attendance.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日期归一化 → YYYY-MM-DD。
 * a/b/yyyy（或 . -）：默认按月/日/年；月不能 &gt;12，若一侧 &gt;12 则该侧为日、另一侧为月。
 * 例：12/06/2026→2026-12-06；13/06/2026→2026-06-13；06/13/2026→2026-06-13。
 * 已是 YYYY-MM-DD 时：月份合法则原样通过；月份非法（13–99）且日在 1–12 则交换月日；否则置空。
 * 有 DATE_RAW 时按段1=月、段2=日重装（方式二），覆盖模型 Date。
 */
public final class RecognizedDateNormalizer {

    private static final Pattern CANONICAL = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})$");
    private static final Pattern MDY4 = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
    private static final Pattern MDY2 = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2})$");
    /** DATE_RAW：格子从左到右三段，段1=月、段2=日、段3=年 */
    private static final Pattern DATE_RAW = Pattern.compile("^(\\d{1,2})[/.,\\-\\s]+(\\d{1,2})[/.,\\-\\s]+(\\d{4}|\\d{2})$");

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

        String isoCorrected = correctIllegalIsoMonth(str);
        if (isoCorrected != null) {
            return isoCorrected;
        }

        Matcher ymd = YMD.matcher(str);
        if (ymd.matches()) {
            return resolveYearFirst(ymd.group(1),
                    Integer.parseInt(ymd.group(2)),
                    Integer.parseInt(ymd.group(3)),
                    str);
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

    /**
     * 方式二：有 DATE_RAW 时按段1=月、段2=日重装，覆盖模型 Date。
     * DATE_RAW 为空则回退 {@link #normalizeDate(String)}。
     */
    public static String applyDateWithRaw(String date, String dateRaw) {
        String raw = dateRaw == null ? "" : dateRaw.trim();
        if (raw.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(raw)) {
            return normalizeDate(date);
        }
        String built = buildFromDateRaw(raw);
        return built != null ? built : "";
    }

    public static boolean isDateRawToken(String value) {
        if (value == null) {
            return false;
        }
        String str = value.trim();
        return !str.isEmpty() && DATE_RAW.matcher(str).matches();
    }

    public static String sanitizeDateRaw(String value) {
        if (!isDateRawToken(value)) {
            return "";
        }
        Matcher m = DATE_RAW.matcher(value.trim());
        if (!m.matches()) {
            return "";
        }
        return m.group(1) + "/" + m.group(2) + "/" + m.group(3);
    }

    /** 段1=月、段2=日；段1不在 1–12 且段2是月则交换。非法则 null。 */
    public static String buildFromDateRaw(String dateRaw) {
        if (dateRaw == null) {
            return null;
        }
        Matcher m = DATE_RAW.matcher(dateRaw.trim());
        if (!m.matches()) {
            return null;
        }
        int seg1 = Integer.parseInt(m.group(1));
        int seg2 = Integer.parseInt(m.group(2));
        int year = expandYear(m.group(3));
        if (seg1 >= 1 && seg1 <= 12 && seg2 >= 1 && seg2 <= 31) {
            return buildCanonical(String.valueOf(year), seg1, seg2);
        }
        if ((seg1 < 1 || seg1 > 12) && seg2 >= 1 && seg2 <= 12 && seg1 >= 1 && seg1 <= 31) {
            return buildCanonical(String.valueOf(year), seg2, seg1);
        }
        return null;
    }

    private static int expandYear(String token) {
        int year = Integer.parseInt(token);
        return token.length() <= 2 ? 2000 + year : year;
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

    /**
     * 模型已输出 YYYY-MM-DD 但月份位非法（如 2026-25-12）。
     * 月份 1–12：不交换（含 2026-02-07 这种歧义合法日期）。
     * 月份非法且日在 1–12：交换；交换后仍非法或两侧都不合法：置空。
     * 非 YYYY-MM-DD：返回 null，交给后续解析。
     */
    private static String correctIllegalIsoMonth(String str) {
        Matcher m = CANONICAL.matcher(str);
        if (!m.matches()) {
            return null;
        }
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        if (month >= 1 && month <= 12) {
            return str;
        }
        if (day >= 1 && day <= 12) {
            String swapped = buildCanonical(m.group(1), day, month);
            return swapped != null ? swapped : "";
        }
        return "";
    }

    /** YYYY/M/D：中间段为月；月非法且末段为合法月则交换，否则置空。 */
    private static String resolveYearFirst(String year, int month, int day, String fallback) {
        String built = buildCanonical(year, month, day);
        if (built != null) {
            return built;
        }
        if (month < 1 || month > 12) {
            if (day >= 1 && day <= 12) {
                String swapped = buildCanonical(year, day, month);
                return swapped != null ? swapped : "";
            }
            return "";
        }
        return fallback;
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
