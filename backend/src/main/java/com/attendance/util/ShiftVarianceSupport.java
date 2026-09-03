package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 班次到离偏差（与 shared/js/shiftVarianceCore.cjs 对齐）。
 */
public final class ShiftVarianceSupport {

    private static final Pattern SHIFT_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}|\\d{1,2}[hH]\\d{0,2})\\s*[-~–—]\\s*(\\d{1,2}:\\d{2}|\\d{1,2}[hH]\\d{0,2})");

    private ShiftVarianceSupport() {
    }

    public static final class Variance {
        public final int earlyArrivalMin;
        public final int lateArrivalMin;
        public final int earlyLeaveMin;
        public final int overtimeMin;

        public Variance(int earlyArrivalMin, int lateArrivalMin, int earlyLeaveMin, int overtimeMin) {
            this.earlyArrivalMin = earlyArrivalMin;
            this.lateArrivalMin = lateArrivalMin;
            this.earlyLeaveMin = earlyLeaveMin;
            this.overtimeMin = overtimeMin;
        }

        public boolean isEmpty() {
            return earlyArrivalMin <= 0 && lateArrivalMin <= 0 && earlyLeaveMin <= 0 && overtimeMin <= 0;
        }
    }

    public static Variance compute(JSONObject record) {
        Variance empty = new Variance(0, 0, 0, 0);
        if (record == null || Boolean.TRUE.equals(record.getBoolean("isDeleted"))) {
            return empty;
        }
        String smartMark = RecordJsonSupport.pickJson(record, "SmartMark", "Mark", "smartMark");
        if (smartMark.contains("未出勤")) {
            return empty;
        }

        ShiftSchedule shift = parseShiftSchedule(
                RecordJsonSupport.pickJson(record, "HORAIRES_DU_TRAVAIL", "shift"));
        if (shift == null) {
            return empty;
        }

        int arriveMin = parseClockToMinutes(RecordJsonSupport.pickJson(record, "ARRIVEE", "arrival"));
        int departMinRaw = parseClockToMinutes(
                RecordJsonSupport.pickJson(record, "DEPAR", "DEPART", "departure"));

        int earlyArrivalMin = 0;
        int lateArrivalMin = 0;
        if (arriveMin >= 0) {
            int delta = arriveMin - shift.startMinutes;
            if (delta < 0) {
                earlyArrivalMin = -delta;
            } else if (delta > 0) {
                lateArrivalMin = delta;
            }
        }

        int earlyLeaveMin = 0;
        int overtimeMin = 0;
        if (departMinRaw >= 0) {
            int departMin = departMinRaw;
            int endMin = shift.endMinutes;
            if (endMin < shift.startMinutes) {
                endMin += 24 * 60;
                if (departMin < shift.startMinutes) {
                    departMin += 24 * 60;
                }
            } else if (departMin < shift.startMinutes - 12 * 60) {
                departMin += 24 * 60;
            }
            int delta = departMin - endMin;
            if (delta < 0) {
                earlyLeaveMin = -delta;
            } else if (delta > 0) {
                overtimeMin = delta;
            }
        }
        return new Variance(earlyArrivalMin, lateArrivalMin, earlyLeaveMin, overtimeMin);
    }

    /** 中文默认句：员工早到 x 且晚离开 y */
    public static String formatSentenceZh(JSONObject record) {
        Variance variance = compute(record);
        if (variance.isEmpty()) {
            return "";
        }
        List<String> phrases = new ArrayList<>();
        if (variance.earlyArrivalMin > 0) {
            phrases.add("早到 " + formatDurationZh(variance.earlyArrivalMin));
        }
        if (variance.lateArrivalMin > 0) {
            phrases.add("迟到 " + formatDurationZh(variance.lateArrivalMin));
        }
        if (variance.earlyLeaveMin > 0) {
            phrases.add("早离开 " + formatDurationZh(variance.earlyLeaveMin));
        }
        if (variance.overtimeMin > 0) {
            phrases.add("晚离开 " + formatDurationZh(variance.overtimeMin));
        }
        if (phrases.isEmpty()) {
            return "";
        }
        return "员工" + String.join("且", phrases);
    }

    static int parseClockToMinutes(String timeStr) {
        if (timeStr == null) {
            return -1;
        }
        String str = timeStr.trim();
        if (str.isEmpty() || "???".equals(str) || "??".equals(str)
                || "illegible".equalsIgnoreCase(str)) {
            return -1;
        }
        Matcher colon = Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(str);
        if (colon.matches()) {
            int h = Integer.parseInt(colon.group(1));
            int m = Integer.parseInt(colon.group(2));
            if (h > 23 || m > 59) {
                return -1;
            }
            return h * 60 + m;
        }
        Matcher hm = Pattern.compile("^(\\d{1,2})[hH](\\d{2})?$").matcher(str);
        if (hm.matches()) {
            int h = Integer.parseInt(hm.group(1));
            int m = hm.group(2) == null ? 0 : Integer.parseInt(hm.group(2));
            if (h > 23 || m > 59) {
                return -1;
            }
            return h * 60 + m;
        }
        return -1;
    }

    private static ShiftSchedule parseShiftSchedule(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Matcher match = SHIFT_PATTERN.matcher(raw.trim());
        if (!match.find()) {
            return null;
        }
        int start = parseClockToMinutes(match.group(1));
        int end = parseClockToMinutes(match.group(2));
        if (start < 0 || end < 0) {
            return null;
        }
        return new ShiftSchedule(start, end);
    }

    private static String formatDurationZh(int totalMinutes) {
        int total = Math.max(0, totalMinutes);
        int hours = total / 60;
        int minutes = total % 60;
        if (total < 60) {
            return total + " min";
        }
        if (minutes == 0) {
            return hours + " h";
        }
        return hours + " h " + minutes + " min";
    }

    private static final class ShiftSchedule {
        final int startMinutes;
        final int endMinutes;

        ShiftSchedule(int startMinutes, int endMinutes) {
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
        }
    }
}
