package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Map;

/**
 * 员工发号比对：工作地区 + 中介 + match_name（默认含流水号，非重名则去流水号）。
 */
public final class EmployeeMatchSupport {

    private EmployeeMatchSupport() {
    }

    /** 考勤行是否已绑定系统员工主键（employees.id）；已绑定则不再发号或改绑。 */
    public static boolean hasSystemEmployeeId(Map<String, Object> record) {
        if (record == null) {
            return false;
        }
        Object employeeId = record.get("employeeId");
        if (employeeId instanceof Number) {
            return ((Number) employeeId).longValue() > 0;
        }
        if (employeeId != null) {
            String text = String.valueOf(employeeId).trim();
            if (!text.isEmpty()) {
                try {
                    return Long.parseLong(text) > 0;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    public static boolean eligibleForEmployeeAssignment(Map<String, Object> record) {
        if (record == null) {
            return false;
        }
        if (Boolean.TRUE.equals(record.get("deleted")) || Boolean.TRUE.equals(record.get("isDeleted"))) {
            return false;
        }
        JSONObject json = new JSONObject(record);
        if (RowReadabilitySupport.isAbsentRow(json)) {
            return false;
        }
        String name = pickName(record);
        String agency = pickAgency(record);
        return !RecordJsonSupport.isBlank(name) && !RecordJsonSupport.isBlank(agency);
    }

    public static String resolveMatchName(Map<String, Object> record) {
        String name = pickName(record);
        if (RecordJsonSupport.isBlank(name)) {
            return "";
        }
        if (isDuplicateConfirmedUnique(record)) {
            return RecordJsonSupport.stripSerialSuffix(name).trim();
        }
        return name.trim();
    }

    public static String resolveAgencyKey(Map<String, Object> record) {
        return RecordJsonSupport.upper(pickAgency(record));
    }

    public static String normalizeRegionCode(String regionCode) {
        if (regionCode == null || regionCode.trim().isEmpty()) {
            return "DEFAULT";
        }
        String normalized = CountryResolver.normalize(regionCode);
        return "default".equalsIgnoreCase(normalized) ? "DEFAULT" : normalized;
    }

    public static String formatEmpNo(String regionCode, int sequence) {
        String prefix = normalizeRegionCode(regionCode);
        return prefix + String.format("%05d", sequence);
    }

    private static boolean isDuplicateConfirmedUnique(Map<String, Object> record) {
        Object flag = record.get("_duplicateConfirmedUnique");
        if (Boolean.TRUE.equals(flag)) {
            return true;
        }
        return "true".equalsIgnoreCase(String.valueOf(flag));
    }

    private static String pickName(Map<String, Object> record) {
        return firstNonBlank(record, "NOM_PRENOM", "NOM", "NAME", "Name");
    }

    private static String pickAgency(Map<String, Object> record) {
        return firstNonBlank(record, "AGENCE_INTERIMAIRE", "AGENCE", "Agency");
    }

    private static String firstNonBlank(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            if (record.containsKey(key)) {
                String value = trim(record.get(key));
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
