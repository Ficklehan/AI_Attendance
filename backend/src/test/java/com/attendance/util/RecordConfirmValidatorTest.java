package com.attendance.util;

import com.attendance.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordConfirmValidatorTest {

    @Test
    void rejectsRecordMissingName() {
        Map<String, Object> record = validRecord();
        record.remove("NOM_PRENOM");
        assertThrows(BusinessException.class, () -> RecordConfirmValidator.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void rejectsRecordMissingDate() {
        Map<String, Object> record = validRecord();
        record.remove("Date");
        assertThrows(BusinessException.class, () -> RecordConfirmValidator.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void allowsMissingNoAndTimes() {
        Map<String, Object> record = validRecord();
        record.remove("NO");
        record.remove("ARRIVEE");
        record.remove("DEPAR");
        record.remove("PAUSE");
        assertDoesNotThrow(() -> RecordConfirmValidator.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void skipsDeletedAndAbsentRows() {
        Map<String, Object> deleted = validRecord();
        deleted.put("isDeleted", true);
        deleted.remove("NOM_PRENOM");

        Map<String, Object> absent = validRecord();
        absent.remove("NOM_PRENOM");
        absent.put("SmartMark", "未出勤");

        assertDoesNotThrow(() -> RecordConfirmValidator.validateConfirmRecords(Arrays.asList(deleted, absent)));
    }

    private static Map<String, Object> validRecord() {
        Map<String, Object> record = new HashMap<>();
        record.put("NO", "001");
        record.put("NOM_PRENOM", "Alice");
        record.put("Date", "2026-05-20");
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "17:00");
        record.put("PAUSE", 60);
        return record;
    }
}
