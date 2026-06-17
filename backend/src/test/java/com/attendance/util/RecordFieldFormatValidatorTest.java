package com.attendance.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordFieldFormatValidatorTest {

    @Test
    void exemptAbsentRowAllowsReposInShift() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("SmartMark", "未出勤");
        Map<String, Object> record = row("Repos", "08:30", "bad", extra);
        assertTrue(RecordFieldFormatValidator.getInvalidFormatFieldKeys(record).isEmpty());
    }

    @Test
    void shiftRequiresExactlyOnePair() {
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow("08:30-17:30"), false);
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow("14H30 - 21H"), false);
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow("22:00-07:00"), false);
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow("08:00-12:00 / 13:00-17:00"), true);
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow("Repos"), true);
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow(""), false);
        assertInvalid("HORAIRES_DU_TRAVAIL", shiftRow("???"), false);
    }

    @Test
    void arrivalDepartureRequireSingleTime() {
        assertInvalid("ARRIVEE", timeRow("ARRIVEE", "08:30"), false);
        assertInvalid("ARRIVEE", timeRow("ARRIVEE", "8:30"), false);
        assertInvalid("ARRIVEE", timeRow("ARRIVEE", "14H30"), false);
        assertInvalid("ARRIVEE", timeRow("ARRIVEE", "21H"), false);
        assertInvalid("ARRIVEE", timeRow("ARRIVEE", "08:30-17:30"), true);
        assertInvalid("DEPAR", timeRow("DEPAR", "17:30"), false);
        assertInvalid("DEPAR", timeRow("DEPAR", "18h00"), false);
        assertInvalid("DEPAR", timeRow("DEPAR", "Repos"), true);
    }

    @Test
    void dateRequiresCanonicalFormat() {
        assertInvalid("Date", dateRow("2026-06-12"), false);
        assertInvalid("Date", dateRow("03/04/2026"), true);
        assertInvalid("Date", dateRow("not-a-date"), true);
        assertInvalid("Date", dateRow(""), false);
    }

    private static Map<String, Object> dateRow(String date) {
        Map<String, Object> record = row("08:30-17:30", "08:30", "17:30", new HashMap<>());
        record.put("Date", date);
        return record;
    }

    private static void assertInvalid(String field, Map<String, Object> record, boolean expectedInvalid) {
        List<String> invalid = RecordFieldFormatValidator.getInvalidFormatFieldKeys(record);
        assertEquals(expectedInvalid, invalid.contains(field));
    }

    private static Map<String, Object> shiftRow(String shift) {
        return row(shift, "08:30", "17:30", new HashMap<>());
    }

    private static Map<String, Object> timeRow(String key, String value) {
        Map<String, Object> record = row("08:30-17:30", "08:30", "17:30", new HashMap<>());
        record.put(key, value);
        return record;
    }

    private static Map<String, Object> row(String shift, String arrivee, String depar, Map<String, Object> extra) {
        Map<String, Object> record = new HashMap<>(extra);
        record.put("HORAIRES_DU_TRAVAIL", shift);
        record.put("ARRIVEE", arrivee);
        record.put("DEPAR", depar);
        return record;
    }

    @Test
    void extractTimeTokensFindsPairs() {
        assertEquals(2, RecordFieldFormatValidator.extractTimeTokens("08:30-17:30").size());
        assertEquals(2, RecordFieldFormatValidator.extractTimeTokens("14H30 - 21H").size());
        assertEquals(4, RecordFieldFormatValidator.extractTimeTokens("08:00-12:00 / 13:00-17:00").size());
        assertTrue(RecordFieldFormatValidator.extractTimeTokens("Repos").isEmpty());
    }
}
