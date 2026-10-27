package com.attendance.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecognizedTextNormalizerTest {

    @Test
    void normalizeWorkerNo_keepsAlphanumeric() {
        assertEquals("A123", RecognizedTextNormalizer.normalizeWorkerNo("A123"));
        assertEquals("12345", RecognizedTextNormalizer.normalizeWorkerNo("12345"));
        assertEquals("EMP99X", RecognizedTextNormalizer.normalizeWorkerNo("[EMP99X]"));
    }

    @Test
    void normalizeWorkerNo_stripsBracketsAndQuotes() {
        assertEquals("123", RecognizedTextNormalizer.normalizeWorkerNo("\"123\""));
        assertEquals("A1", RecognizedTextNormalizer.normalizeWorkerNo("【A1】"));
    }

    @Test
    void normalizePersonName_keepsNonEmptyParentheses() {
        assertEquals("Jean (temp)", RecognizedTextNormalizer.normalizePersonName("Jean (temp)"));
        assertEquals("Jean（temp）", RecognizedTextNormalizer.normalizePersonName("Jean（temp）"));
    }

    @Test
    void normalizePersonName_stripsQuestionMarksAndKeepsBlurToken() {
        assertEquals("???", RecognizedTextNormalizer.normalizePersonName("???"));
        assertEquals("???", RecognizedTextNormalizer.normalizePersonName("?"));
        assertEquals("Jean Dupont", RecognizedTextNormalizer.normalizePersonName("Jean? Dupont"));
        assertEquals("Jean", RecognizedTextNormalizer.normalizePersonName("Jean？"));
    }

    @Test
    void normalizePersonName_removesEmptyParentheses() {
        assertEquals("Jean", RecognizedTextNormalizer.normalizePersonName("Jean ()"));
        assertEquals("Jean", RecognizedTextNormalizer.normalizePersonName("Jean（）"));
        assertEquals("Jean", RecognizedTextNormalizer.normalizePersonName("Jean()"));
    }

    @Test
    void normalizeLabelText_stripsBrackets() {
        assertEquals("Warehouse A", RecognizedTextNormalizer.normalizeLabelText("[Warehouse A]"));
    }

    @Test
    void normalizeRecordFields_keepsUserEditedDateOverDateRaw() {
        Map<String, Object> record = new HashMap<>();
        record.put("Date", "2026-09-09");
        record.put("DATE_RAW", "09/07/2026");
        RecognizedTextNormalizer.normalizeRecordFields(record);
        assertEquals("2026-09-09", record.get("Date"));
        assertEquals("", record.get("DATE_RAW"));
    }

    @Test
    void normalizeRecordFields_keepsDateRawWhenConsistent() {
        Map<String, Object> record = new HashMap<>();
        record.put("Date", "2026-09-07");
        record.put("DATE_RAW", "09/07/2026");
        RecognizedTextNormalizer.normalizeRecordFields(record);
        assertEquals("2026-09-07", record.get("Date"));
        assertEquals("09/07/2026", record.get("DATE_RAW"));
    }
}
