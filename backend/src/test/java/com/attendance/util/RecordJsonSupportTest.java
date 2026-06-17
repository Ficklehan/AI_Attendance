package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordJsonSupportTest {

    @Test
    void clampVarchar_truncatesLongValues() {
        String longDate = "This is not a date but OCR garbage that exceeds thirty two characters easily";
        assertEquals(32, RecordJsonSupport.clampVarchar(longDate, 32).length());
    }

    @Test
    void clampVarchar_keepsShortValues() {
        assertEquals("2026-06-17", RecordJsonSupport.clampVarchar("2026-06-17", 32));
    }
}
