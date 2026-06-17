package com.attendance.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书同步前派生字段：到达/离开 datetime、出勤工时。供确认提交与校准同步复用。
 */
public final class RecordFeishuPrepareSupport {

    private static final Pattern CLOCK = Pattern.compile("^\\d{1,2}:\\d{2}$");

    private RecordFeishuPrepareSupport() {
    }

    public static void prepareRecord(Map<String, Object> record) {
        if (record == null) {
            return;
        }
        enrichDatetimeFields(record);
        enrichWorkHours(record);
    }

    public static void enrichDatetimeFields(Map<String, Object> record) {
        String baseDate = stringValue(record.get("Date"));
        String arrive = stringValue(record.get("ARRIVEE"));
        String depart = stringValue(record.get("DEPAR"));

        if (RecognizedFieldSanitizer.isUnrecognized(baseDate)
                || RecognizedFieldSanitizer.isUnrecognized(arrive)
                || RecognizedFieldSanitizer.isUnrecognized(depart)) {
            return;
        }

        String normalizedArrive = RecognizedTimeNormalizer.normalizeClockTime(arrive);
        String normalizedDepart = RecognizedTimeNormalizer.normalizeClockTime(depart);
        if (!isClockTime(normalizedArrive) || !isClockTime(normalizedDepart)) {
            return;
        }

        int arriveHour = Integer.parseInt(normalizedArrive.split(":")[0]);
        int departHour = Integer.parseInt(normalizedDepart.split(":")[0]);

        String arriveDateStr;
        String departDateStr;
        if (arriveHour >= 18 && departHour <= 12) {
            arriveDateStr = baseDate;
            departDateStr = addDays(baseDate, 1);
        } else {
            arriveDateStr = baseDate;
            departDateStr = baseDate;
        }

        record.put("ARRIVEE_DATE", arriveDateStr);
        record.put("DEPAR_DATE", departDateStr);
        record.put("ARRIVEE_DATETIME", arriveDateStr + " " + normalizedArrive);
        record.put("DEPAR_DATETIME", departDateStr + " " + normalizedDepart);
    }

    public static void enrichWorkHours(Map<String, Object> record) {
        if (isDeletedOrAbsent(record)) {
            record.remove("WorkHours");
            return;
        }

        String arriveStr = stringValue(record.get("ARRIVEE"));
        String departStr = stringValue(record.get("DEPAR"));
        if (RecognizedFieldSanitizer.isUnrecognized(arriveStr) || RecognizedFieldSanitizer.isUnrecognized(departStr)) {
            record.remove("WorkHours");
            return;
        }

        Integer arriveMinutes = parseTimeToMinutes(arriveStr);
        Integer departMinutes = parseTimeToMinutes(departStr);
        if (arriveMinutes == null || departMinutes == null) {
            record.remove("WorkHours");
            return;
        }

        int totalMinutes = departMinutes - arriveMinutes;
        if (totalMinutes < 0) {
            totalMinutes += 24 * 60;
        }

        int pause = parsePauseToMinutes(record.get("PAUSE"));
        int workMinutes = totalMinutes - pause;
        if (workMinutes < 0) {
            record.remove("WorkHours");
            return;
        }

        double workHours = Math.round(workMinutes * 100.0 / 60.0) / 100.0;
        record.put("WorkHours", workHours);
    }

    private static boolean isDeletedOrAbsent(Map<String, Object> record) {
        if (Boolean.TRUE.equals(record.get("isDeleted"))) {
            return true;
        }
        Object smartMark = record.get("SmartMark");
        return smartMark != null && smartMark.toString().contains("未出勤");
    }

    static String normalizeTime(String timeStr) {
        return RecognizedTimeNormalizer.normalizeClockTime(timeStr);
    }

    private static boolean isClockTime(String value) {
        return value != null && CLOCK.matcher(value).matches();
    }

    private static String addDays(String dateStr, int days) {
        if (dateStr == null) {
            return dateStr;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            cal.add(Calendar.DAY_OF_MONTH, days);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            return dateStr;
        }
    }

    static Integer parseTimeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || RecognizedFieldSanitizer.isUnrecognized(timeStr)) {
            return null;
        }
        try {
            String cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':');
            String[] parts = cleanTime.split(":");
            if (parts.length == 2) {
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                return hours * 60 + minutes;
            }
            if (parts.length == 1) {
                double num = Double.parseDouble(parts[0].trim());
                return (int) (Math.floor(num) * 60 + Math.round((num % 1) * 60));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    static int parsePauseToMinutes(Object pauseValue) {
        if (pauseValue == null) {
            return 0;
        }
        String raw = String.valueOf(pauseValue).trim();
        if (raw.isEmpty() || RecognizedFieldSanitizer.isUnrecognized(raw)) {
            return 0;
        }
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replace(',', '.')
                .replaceAll("\\s+", "")
                .replace("minutes", "min")
                .replace("minute", "min")
                .replace("mins", "min")
                .replace("mn", "min");
        try {
            Matcher hourMinute = Pattern.compile("^(\\d+(?:\\.\\d+)?)h(\\d+(?:\\.\\d+)?)?(?:min|m)?$")
                    .matcher(normalized);
            if (hourMinute.matches()) {
                double hours = Double.parseDouble(hourMinute.group(1));
                double minutes = hourMinute.group(2) == null || hourMinute.group(2).isEmpty()
                        ? 0
                        : Double.parseDouble(hourMinute.group(2));
                return (int) Math.round(hours * 60 + minutes);
            }

            Matcher colon = Pattern.compile("^(\\d{1,2}):(\\d{1,2})$").matcher(normalized);
            if (colon.matches()) {
                return Integer.parseInt(colon.group(1)) * 60 + Integer.parseInt(colon.group(2));
            }

            Matcher minute = Pattern.compile("^(\\d+(?:\\.\\d+)?)(?:min|m)?$").matcher(normalized);
            if (minute.matches()) {
                return (int) Math.round(Double.parseDouble(minute.group(1)));
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
