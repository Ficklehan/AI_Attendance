package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates required fields on task confirm. Rows marked absent or deleted are exempt.
 */
public final class RecordConfirmValidator {

    private static final List<String> REQUIRED_KEYS = Arrays.asList("NOM_PRENOM", "Date");

    private RecordConfirmValidator() {
    }

    public static void validateConfirmRecords(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Map<String, Object> record : records) {
            if (hasRequiredMissing(record)) {
                throw new BusinessException(400, ErrorKeys.CONFIRM_REQUIRED_FIELDS_MISSING);
            }
        }
    }

    static boolean hasRequiredMissing(Map<String, Object> record) {
        if (isExempt(record)) {
            return false;
        }
        for (String key : REQUIRED_KEYS) {
            if (isFieldMissing(record, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExempt(Map<String, Object> record) {
        if (record == null) {
            return true;
        }
        if (Boolean.TRUE.equals(record.get("isDeleted")) || Boolean.TRUE.equals(record.get("deleted"))) {
            return true;
        }
        String mark = stringValue(record.get("SmartMark"));
        if (mark.isEmpty()) {
            mark = stringValue(record.get("Mark"));
        }
        if (containsAny(mark, "已删除", "deleted", "eliminado", "gelöscht", "verwijderd")) {
            return true;
        }
        if (Boolean.TRUE.equals(record.get("_restored"))) {
            return false;
        }
        return containsAny(mark, "未出勤", "absent", "ausente", "abwesend", "afwezig");
    }

    private static boolean isFieldMissing(Map<String, Object> record, String key) {
        if ("NOM_PRENOM".equals(key)) {
            return !hasFilledText(firstNonBlank(record, "NOM_PRENOM", "Name"));
        }
        if ("Date".equals(key)) {
            return !hasFilledText(firstNonBlank(record, "Date", "WorkDate"));
        }
        return !hasFilledText(record.get(key));
    }

    private static String firstNonBlank(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            String v = stringValue(record.get(key));
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static boolean hasFilledText(Object value) {
        return !RecognizedFieldSanitizer.isUnrecognized(stringValue(value));
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static boolean containsAny(String text, String... tokens) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
