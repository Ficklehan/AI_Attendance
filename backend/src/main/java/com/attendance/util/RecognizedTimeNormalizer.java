package com.attendance.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别结果时间归一化：欧洲写法 14H30 / 21H → 14:30 / 21:00；班次取前两个时间点。
 */
public final class RecognizedTimeNormalizer {

  /** 14H30、14:30、21H（整点） */
  private static final Pattern TIME_IN_TEXT = Pattern.compile(
      "(?i)(\\d{1,2})[:hH](\\d{2})|(\\d{1,2})[hH](?!\\d)");

  private RecognizedTimeNormalizer() {
  }

  public static String normalizeClockTime(String timeStr) {
    if (timeStr == null || timeStr.trim().isEmpty()) {
      return timeStr;
    }
    String str = timeStr.trim();
    if (RecognizedFieldSanitizer.isUnrecognized(str)) {
      return "";
    }

    if (str.matches("\\d{1,2}:\\d{2}")) {
      String[] parts = str.split(":");
      return String.format("%02d:%s", Integer.parseInt(parts[0]), parts[1]);
    }

    Matcher matcherH = Pattern.compile("^(\\d{1,2})[hH]$", Pattern.CASE_INSENSITIVE).matcher(str);
    if (matcherH.matches()) {
      return String.format("%02d:00", Integer.parseInt(matcherH.group(1)));
    }

    Matcher matcherHM = Pattern.compile("^(\\d{1,2})[hH](\\d{1,2})$", Pattern.CASE_INSENSITIVE).matcher(str);
    if (matcherHM.matches()) {
      return String.format("%02d:%02d",
          Integer.parseInt(matcherHM.group(1)),
          Integer.parseInt(matcherHM.group(2)));
    }

    Matcher matcherComma = Pattern.compile("^(\\d{1,2})[,.](\\d{1,2})$").matcher(str);
    if (matcherComma.matches()) {
      return String.format("%02d:%02d",
          Integer.parseInt(matcherComma.group(1)),
          Integer.parseInt(matcherComma.group(2)));
    }

    Matcher matcher4Digits = Pattern.compile("^(\\d{2})(\\d{2})$").matcher(str);
    if (matcher4Digits.matches()) {
      return matcher4Digits.group(1) + ":" + matcher4Digits.group(2);
    }

    Matcher matcher1Digit = Pattern.compile("^(\\d)$").matcher(str);
    if (matcher1Digit.matches()) {
      return String.format("%02d:00", Integer.parseInt(matcher1Digit.group(1)));
    }

    return str;
  }

  /**
   * 班次：从文本中提取前两个时间点并规范为 HH:MM-HH:MM，如 14H30 - 21H → 14:30-21:00。
   */
  public static String normalizeShiftSchedule(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return raw;
    }
    String str = raw.trim();
    if (RecognizedFieldSanitizer.isUnrecognized(str)) {
      return "";
    }
    List<String> tokens = extractTimeTokenStrings(str);
    if (tokens.size() >= 2) {
      return normalizeClockTime(tokens.get(0)) + "-" + normalizeClockTime(tokens.get(1));
    }
    if (tokens.size() == 1) {
      return normalizeClockTime(tokens.get(0));
    }
    return str;
  }

  static List<String> extractTimeTokenStrings(String raw) {
    List<String> tokens = new ArrayList<>();
    if (raw == null || raw.trim().isEmpty()) {
      return tokens;
    }
    Matcher matcher = TIME_IN_TEXT.matcher(raw.trim());
    while (matcher.find()) {
      String token = matcher.group(0);
      if (token != null && !token.isEmpty()) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  static boolean isValidClockTime(String value) {
    if (value == null || value.trim().isEmpty()) {
      return false;
    }
    return value.trim().matches("\\d{1,2}:\\d{2}");
  }

  /** 法文考勤表常见非时间标注（OCR 误入到/离/班次列），无时间点时视为无法辨认 */
  public static boolean isNonTimeFieldLabel(String value) {
    if (value == null || value.trim().isEmpty()) {
      return false;
    }
    if (!extractTimeTokenStrings(value).isEmpty()) {
      return false;
    }
    String lower = value.trim().toLowerCase(Locale.ROOT);
    if ("repos".equals(lower)) {
      return true;
    }
    if (lower.contains("fin de mission")) {
      return true;
    }
    if (lower.startsWith("pas de") || "pas".equals(lower)) {
      return true;
    }
    if ("début".equals(lower) || "debut".equals(lower) || "fin".equals(lower)) {
      return true;
    }
    return lower.startsWith("sans ");
  }
}
