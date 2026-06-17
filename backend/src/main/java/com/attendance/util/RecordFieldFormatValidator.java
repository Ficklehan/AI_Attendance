package com.attendance.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Business format rules for confirm submit: shift must be one start–end pair;
 * arrival/departure must be a single time. Empty values skip format check.
 */
public final class RecordFieldFormatValidator {

    private static final List<String> FORMAT_FIELD_KEYS = Arrays.asList(
            "Date", "HORAIRES_DU_TRAVAIL", "ARRIVEE", "DEPAR");

    private RecordFieldFormatValidator() {
    }

    public static List<String> getInvalidFormatFieldKeys(Map<String, Object> record) {
        if (ConfirmValidationExempt.isExempt(record)) {
            return new ArrayList<>();
        }
        List<String> invalid = new ArrayList<>();
        for (String key : FORMAT_FIELD_KEYS) {
            if (isSingleFieldFormatInvalid(record, key)) {
                invalid.add(key);
            }
        }
        if (isArrivalDepartureSameTime(record)) {
            for (String key : Arrays.asList("ARRIVEE", "DEPAR")) {
                if (!invalid.contains(key)) {
                    invalid.add(key);
                }
            }
        }
        return invalid;
    }

    static boolean isArrivalDepartureSameTime(Map<String, Object> record) {
        if (ConfirmValidationExempt.isExempt(record)) {
            return false;
        }
        String arrive = pickField(record, "ARRIVEE");
        String depart = pickField(record, "DEPAR");
        if (shouldSkipFormatCheck(arrive) || shouldSkipFormatCheck(depart)) {
            return false;
        }
        List<TimeToken> arriveTokens = extractTimeTokens(arrive);
        List<TimeToken> departTokens = extractTimeTokens(depart);
        if (arriveTokens.size() != 1 || departTokens.size() != 1) {
            return false;
        }
        TimeToken a = arriveTokens.get(0);
        TimeToken d = departTokens.get(0);
        return a.hours == d.hours && a.minutes == d.minutes;
    }

    static boolean isFieldFormatInvalid(Map<String, Object> record, String fieldKey) {
        if (("ARRIVEE".equals(fieldKey) || "DEPAR".equals(fieldKey))
                && isArrivalDepartureSameTime(record)) {
            return true;
        }
        return isSingleFieldFormatInvalid(record, fieldKey);
    }

    static boolean isSingleFieldFormatInvalid(Map<String, Object> record, String fieldKey) {
        String value = pickField(record, fieldKey);
        if (shouldSkipFormatCheck(value)) {
            return false;
        }
        if ("Date".equals(fieldKey)) {
            return RecognizedDateNormalizer.isDateFormatInvalid(value);
        }
        if ("HORAIRES_DU_TRAVAIL".equals(fieldKey)) {
            return extractTimeTokens(value).size() != 2;
        }
        if ("ARRIVEE".equals(fieldKey) || "DEPAR".equals(fieldKey)) {
            return extractTimeTokens(value).size() != 1;
        }
        return false;
    }

    private static boolean shouldSkipFormatCheck(String value) {
        return RecognizedFieldSanitizer.isUnrecognized(value) || value.isEmpty();
    }

    private static String pickField(Map<String, Object> record, String fieldKey) {
        if (record == null) {
            return "";
        }
        switch (fieldKey) {
            case "ARRIVEE":
                return firstNonBlank(record, "ARRIVEE", "arrival");
            case "Date":
                return firstNonBlank(record, "Date", "WorkDate");
            case "DEPAR":
                return firstNonBlank(record, "DEPAR", "DEPART", "departure");
            case "HORAIRES_DU_TRAVAIL":
                return firstNonBlank(record, "HORAIRES_DU_TRAVAIL", "shift");
            default:
                return stringValue(record.get(fieldKey));
        }
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

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    static List<TimeToken> extractTimeTokens(String raw) {
        List<TimeToken> tokens = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return tokens;
        }
        for (String token : RecognizedTimeNormalizer.extractTimeTokenStrings(raw.trim())) {
            String normalized = RecognizedTimeNormalizer.normalizeClockTime(token);
            if (!RecognizedTimeNormalizer.isValidClockTime(normalized)) {
                continue;
            }
            String[] parts = normalized.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59) {
                tokens.add(new TimeToken(hours, minutes, token));
            }
        }
        return tokens;
    }

    static final class TimeToken {
        final int hours;
        final int minutes;
        final String raw;

        TimeToken(int hours, int minutes, String raw) {
            this.hours = hours;
            this.minutes = minutes;
            this.raw = raw;
        }
    }

    /**
     * Shared exempt logic for confirm validation (deleted / absent rows).
     */
    static final class ConfirmValidationExempt {
        private ConfirmValidationExempt() {
        }

        static boolean isExempt(Map<String, Object> record) {
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
            if (!Boolean.TRUE.equals(record.get("_restored"))
                    && containsAny(mark, "未出勤", "absent", "ausente", "abwesend", "afwezig")) {
                return true;
            }
            return false;
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
}
