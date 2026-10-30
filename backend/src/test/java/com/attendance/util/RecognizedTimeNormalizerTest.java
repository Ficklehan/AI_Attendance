package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecognizedTimeNormalizerTest {

    @Test
    void normalizeClockTimeHandlesEuropeanFormats() {
        assertEquals("14:30", RecognizedTimeNormalizer.normalizeClockTime("14H30"));
        assertEquals("21:00", RecognizedTimeNormalizer.normalizeClockTime("21H"));
        assertEquals("08:30", RecognizedTimeNormalizer.normalizeClockTime("8:30"));
        assertEquals("18:00", RecognizedTimeNormalizer.normalizeClockTime("18h00"));
        assertEquals("08:30", RecognizedTimeNormalizer.normalizeClockTime("2026-09-03 08:30:00"));
        assertEquals("08:30", RecognizedTimeNormalizer.normalizeClockTime("2026-09-03T08:30:00.000Z"));
        assertEquals("20:30", RecognizedTimeNormalizer.normalizeClockTime("9/3/2026 8:30:00 PM"));
        assertEquals("08:30", RecognizedTimeNormalizer.normalizeClockTime("08:30:00"));
    }

    @Test
    void normalizeShiftScheduleConvertsEuropeanRange() {
        assertEquals("14:30-21:00", RecognizedTimeNormalizer.normalizeShiftSchedule("14H30 - 21H"));
        assertEquals("08:00-18:00", RecognizedTimeNormalizer.normalizeShiftSchedule("8:00-18:00"));
        assertEquals("08:00-17:30", RecognizedTimeNormalizer.normalizeShiftSchedule("08h00-17h30"));
        String[] pair = RecognizedTimeNormalizer.splitShiftStartEnd("08:00 - 14:00");
        assertEquals("08:00", pair[0]);
        assertEquals("14:00", pair[1]);
    }

    @Test
    void extractTimeTokenStringsFindsHourOnly() {
        assertEquals(2, RecognizedTimeNormalizer.extractTimeTokenStrings("14H30 - 21H").size());
    }

    @Test
    void normalizeClockTimeRejectsHourOutOfRange() {
        assertEquals("", RecognizedTimeNormalizer.normalizeClockTime("50:00"));
        assertEquals("", RecognizedTimeNormalizer.normalizeClockTime("24:00"));
        assertEquals("", RecognizedTimeNormalizer.normalizeClockTime("5000"));
        assertEquals("15:41", RecognizedTimeNormalizer.normalizeClockTime("15:41"));
        assertEquals("13:41", RecognizedTimeNormalizer.normalizeClockTime("13:41"));
        assertEquals("09:43", RecognizedTimeNormalizer.normalizeClockTime("9:43"));
        assertEquals("19:00", RecognizedTimeNormalizer.normalizeClockTime("19:0"));
        assertEquals("12:00", RecognizedTimeNormalizer.normalizeClockTime("12:0"));
        assertEquals("09:00", RecognizedTimeNormalizer.normalizeClockTime("9:0"));
    }

    @Test
    void handwrittenClockRejectsBareDigits() {
        assertEquals("", RecognizedTimeNormalizer.normalizeHandwrittenClock("0"));
        assertEquals("", RecognizedTimeNormalizer.normalizeHandwrittenClock("00"));
        assertEquals("", RecognizedTimeNormalizer.normalizeHandwrittenClock("0010"));
        assertEquals("", RecognizedTimeNormalizer.normalizeHandwrittenClock("8"));
        assertEquals("", RecognizedTimeNormalizer.normalizeHandwrittenClock("280:0"));
        assertEquals("19:00", RecognizedTimeNormalizer.normalizeHandwrittenClock("19:0"));
        assertEquals("10:00", RecognizedTimeNormalizer.normalizeHandwrittenClock("10:00"));
    }
}
