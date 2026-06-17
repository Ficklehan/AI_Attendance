package com.attendance.util;

import org.junit.jupiter.api.Test;

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
        assertEquals("Jean (temp)", RecognizedTextNormalizer.normalizePersonName("Jean（temp）"));
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
}
