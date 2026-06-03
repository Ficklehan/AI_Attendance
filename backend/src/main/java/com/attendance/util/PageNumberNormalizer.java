package com.attendance.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 归一化 Excel 打印页脚/页眉中的页码文本。
 */
public final class PageNumberNormalizer {

    private static final Pattern PAGE_OF = Pattern.compile(
            "(?i)(?:page|p\\.?|pg\\.?)\\s*(\\d{1,4})\\s*(?:of|/|\\/)\\s*(\\d{1,4})",
            Pattern.UNICODE_CASE);
    private static final Pattern SLASH = Pattern.compile("^(\\d{1,4})\\s*/\\s*(\\d{1,4})$");
    private static final Pattern CN_PAGE = Pattern.compile("第\\s*(\\d{1,4})\\s*页(?:\\s*/\\s*(\\d{1,4})\\s*页)?");
    private static final Pattern CN_PAGE_TOTAL = Pattern.compile("共\\s*(\\d{1,4})\\s*页");
    private static final Pattern DASH = Pattern.compile("^[-–—]\\s*(\\d{1,4})\\s*[-–—]$");
    private static final Pattern PLAIN = Pattern.compile("^(\\d{1,4})$");

    private PageNumberNormalizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(text)) {
            return "";
        }
        text = text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();

        Matcher m = PAGE_OF.matcher(text);
        if (m.find()) {
            return m.group(1) + "/" + m.group(2);
        }
        m = SLASH.matcher(text);
        if (m.matches()) {
            return m.group(1) + "/" + m.group(2);
        }
        m = CN_PAGE.matcher(text);
        if (m.find()) {
            if (m.group(2) != null && !m.group(2).isEmpty()) {
                return m.group(1) + "/" + m.group(2);
            }
            return m.group(1);
        }
        m = DASH.matcher(text);
        if (m.matches()) {
            return m.group(1);
        }
        if (text.toLowerCase().startsWith("page ")) {
            String digits = text.replaceAll("(?i)page\\s*", "").trim();
            m = SLASH.matcher(digits);
            if (m.matches()) {
                return m.group(1) + "/" + m.group(2);
            }
            m = PLAIN.matcher(digits);
            if (m.matches()) {
                return m.group(1);
            }
        }
        m = PLAIN.matcher(text);
        if (m.matches()) {
            return m.group(1);
        }
        return "";
    }
}
