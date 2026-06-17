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
    }

    @Test
    void normalizeShiftScheduleConvertsEuropeanRange() {
        assertEquals("14:30-21:00", RecognizedTimeNormalizer.normalizeShiftSchedule("14H30 - 21H"));
        assertEquals("08:00-18:00", RecognizedTimeNormalizer.normalizeShiftSchedule("8:00-18:00"));
        assertEquals("08:00-17:30", RecognizedTimeNormalizer.normalizeShiftSchedule("08h00-17h30"));
    }

    @Test
    void extractTimeTokenStringsFindsHourOnly() {
        assertEquals(2, RecognizedTimeNormalizer.extractTimeTokenStrings("14H30 - 21H").size());
    }
}
