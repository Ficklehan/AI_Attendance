package com.attendance.util;

import com.attendance.dto.NightShiftConfigDTO;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 夜班标记推断（规则可配置，由后端在识别后计算）。
 */
public final class NightShiftMarkSupport {

    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern SHIFT_RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-~–]\\s*(\\d{1,2}:\\d{2})");

    private NightShiftMarkSupport() {
    }

    public static boolean shouldMarkNightShift(String arriveTime,
                                             String departTime,
                                             String shiftSchedule,
                                             NightShiftConfigDTO config) {
        NightShiftConfigDTO rules = config != null ? config : NightShiftConfigDTO.defaults();
        if (hasUsableArriveAndDepart(arriveTime, departTime)) {
            return isNightShiftByTimes(arriveTime, departTime, rules);
        }
        if (isNightShiftByTimes(arriveTime, departTime, rules)) {
            return true;
        }
        return rules.isUseScheduleColumn() && isNightShiftBySchedule(shiftSchedule, rules);
    }

    static boolean hasUsableArriveAndDepart(String arriveTime, String departTime) {
        return parseClockToMinutes(arriveTime) >= 0 && parseClockToMinutes(departTime) >= 0;
    }

    static boolean isNightShiftByTimes(String arriveTime, String departTime, NightShiftConfigDTO config) {
        int arriveMin = parseClockToMinutes(arriveTime);
        int departMin = parseClockToMinutes(departTime);
        if (arriveMin < 0 || departMin < 0) {
            return false;
        }
        int startMin = parseClockToMinutes(config.getStartTime());
        int endMin = parseClockToMinutes(config.getEndTime());
        if (startMin < 0) {
            startMin = 20 * 60;
        }
        if (endMin < 0) {
            endMin = 6 * 60;
        }
        if (arriveMin >= startMin) {
            return true;
        }
        if (departMin < endMin) {
            return true;
        }
        return config.isCrossMidnight() && departMin < arriveMin;
    }

    static boolean isNightShiftBySchedule(String shiftSchedule, NightShiftConfigDTO config) {
        if (shiftSchedule == null || shiftSchedule.trim().isEmpty()) {
            return false;
        }
        Matcher matcher = SHIFT_RANGE.matcher(shiftSchedule.trim());
        if (!matcher.find()) {
            return false;
        }
        int startMin = parseClockToMinutes(matcher.group(1));
        int endMin = parseClockToMinutes(matcher.group(2));
        if (startMin < 0 || endMin < 0) {
            return false;
        }
        int ruleStartMin = parseClockToMinutes(config.getStartTime());
        int ruleEndMin = parseClockToMinutes(config.getEndTime());
        if (ruleStartMin < 0) {
            ruleStartMin = 20 * 60;
        }
        if (ruleEndMin < 0) {
            ruleEndMin = 6 * 60;
        }
        if (endMin < startMin) {
            return true;
        }
        if (startMin >= ruleStartMin) {
            return true;
        }
        return endMin < ruleEndMin;
    }

    static int parseClockToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return -1;
        }
        String str = timeStr.trim();
        if ("???".equals(str) || "illegible".equalsIgnoreCase(str)) {
            return -1;
        }
        Matcher matcher = CLOCK.matcher(str);
        if (!matcher.matches()) {
            return -1;
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    /** 大模型不应输出夜班；剥离后由后端重算。 */
    public static boolean isNightShiftMarkToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        String lower = token.trim().toLowerCase();
        return "夜班".equals(token.trim())
                || lower.contains("night shift")
                || lower.equals("night")
                || lower.contains("nuit")
                || lower.contains("noche")
                || lower.contains("nacht")
                || lower.contains("noc");
    }
}
