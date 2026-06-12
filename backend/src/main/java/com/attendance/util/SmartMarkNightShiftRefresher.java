package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.NightShiftConfigDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按当前到离/排班重算 SmartMark 中的夜班标记（增删均可）。
 */
public final class SmartMarkNightShiftRefresher {

    private SmartMarkNightShiftRefresher() {
    }

    public static String refresh(String smartMark, JSONObject record, NightShiftConfigDTO config) {
        if (record != null && record.getBooleanValue("isDeleted")) {
            return joinWithoutNightShift(smartMark);
        }
        List<String> parts = splitParts(smartMark);
        parts.removeIf(NightShiftMarkSupport::isNightShiftMarkToken);

        if (containsAbsent(parts) || isRecordAbsent(record)) {
            return String.join(";", parts);
        }
        if (parts.isEmpty()) {
            parts.add("正常");
        }

        String arrive = record != null ? record.getString("ARRIVEE") : null;
        String depart = record != null ? record.getString("DEPAR") : null;
        String schedule = record != null ? record.getString("HORAIRES_DU_TRAVAIL") : null;
        if (NightShiftMarkSupport.shouldMarkNightShift(arrive, depart, schedule, config)) {
            if (!parts.contains("夜班")) {
                parts.add("夜班");
            }
        }
        return String.join(";", dedupe(parts));
    }

    static List<String> splitParts(String smartMark) {
        List<String> parts = new ArrayList<>();
        if (smartMark == null || smartMark.trim().isEmpty()) {
            return parts;
        }
        for (String part : smartMark.split("[;；,，]")) {
            String token = part == null ? "" : part.trim();
            if (!token.isEmpty()) {
                parts.add(token);
            }
        }
        return parts;
    }

    private static String joinWithoutNightShift(String smartMark) {
        List<String> parts = splitParts(smartMark);
        parts.removeIf(NightShiftMarkSupport::isNightShiftMarkToken);
        return String.join(";", parts);
    }

    private static boolean containsAbsent(List<String> parts) {
        for (String part : parts) {
            if ("未出勤".equals(part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRecordAbsent(JSONObject record) {
        if (record == null) {
            return false;
        }
        String arrive = record.getString("ARRIVEE");
        String depart = record.getString("DEPAR");
        return isTimeEmpty(arrive) && isTimeEmpty(depart);
    }

    private static boolean isTimeEmpty(String time) {
        if (time == null || time.trim().isEmpty()) {
            return true;
        }
        String trimmed = time.trim();
        return "???".equals(trimmed) || "illegible".equalsIgnoreCase(trimmed);
    }

    private static List<String> dedupe(List<String> parts) {
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            seen.add(part);
        }
        return new ArrayList<>(seen);
    }
}
