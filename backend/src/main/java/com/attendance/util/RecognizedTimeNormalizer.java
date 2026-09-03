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

  /** Excel / ISO / 欧式日期时间，取时刻 */
  private static final Pattern DATE_THEN_TIME = Pattern.compile(
      "(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})[T\\s]+(\\d{1,2})[:hH.](\\d{2})(?::\\d{2})?(?:\\s*([AaPp][Mm]))?");

  private static final Pattern ISO_T_TIME = Pattern.compile("T(\\d{1,2}):(\\d{2})(?::\\d{2})?");

  private static final Pattern CLOCK_SECONDS_OR_AMPM = Pattern.compile(
      "^(\\d{1,2}):(\\d{2})(?::\\d{2})?(?:\\s*([AaPp][Mm]))?$", Pattern.CASE_INSENSITIVE);

  private RecognizedTimeNormalizer() {
  }

  private static String formatClock(int hours, int minutes) {
    if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
      return null;
    }
    return String.format("%02d:%02d", hours, minutes);
  }

  private static int applyAmPm(int hours, String ampm) {
    if (ampm == null || ampm.isEmpty()) {
      return hours;
    }
    char mer = Character.toLowerCase(ampm.charAt(0));
    if (mer == 'a') {
      return hours == 12 ? 0 : hours;
    }
    if (mer == 'p') {
      return hours < 12 ? hours + 12 : hours;
    }
    return hours;
  }

  static String extractClockFromPastedText(String raw) {
    if (raw == null) {
      return null;
    }
    String str = raw.trim();
    if (str.isEmpty()) {
      return null;
    }
    Matcher dated = DATE_THEN_TIME.matcher(str);
    if (dated.find()) {
      return formatClock(applyAmPm(Integer.parseInt(dated.group(1)), dated.group(3)),
          Integer.parseInt(dated.group(2)));
    }
    Matcher isoT = ISO_T_TIME.matcher(str);
    if (isoT.find()) {
      return formatClock(Integer.parseInt(isoT.group(1)), Integer.parseInt(isoT.group(2)));
    }
    Matcher standalone = CLOCK_SECONDS_OR_AMPM.matcher(str);
    if (standalone.matches()) {
      boolean hasAmPm = standalone.group(3) != null;
      boolean hasSeconds = str.matches("\\d{1,2}:\\d{2}:\\d{2}(?:\\s*[AaPp][Mm])?");
      if (hasAmPm || hasSeconds) {
        return formatClock(applyAmPm(Integer.parseInt(standalone.group(1)), standalone.group(3)),
            Integer.parseInt(standalone.group(2)));
      }
    }
    return null;
  }

  public static String normalizeClockTime(String timeStr) {
    if (timeStr == null || timeStr.trim().isEmpty()) {
      return timeStr;
    }
    String str = timeStr.trim();
    if (RecognizedFieldSanitizer.isUnrecognized(str)) {
      return "";
    }

    String pasted = extractClockFromPastedText(str);
    if (pasted != null) {
      return pasted;
    }

    List<String> tokens = extractTimeTokenStrings(str);
    if (tokens.size() == 1 && !str.equals(tokens.get(0))) {
      return normalizeClockTime(tokens.get(0));
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
