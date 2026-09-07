package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecognizedDateNormalizerTest {

    @Test
    void normalizeDate_defaultMonthDayYear() {
        assertEquals("2026-12-06", RecognizedDateNormalizer.normalizeDate("12/06/2026"));
        assertEquals("2026-12-06", RecognizedDateNormalizer.normalizeDate("12.06.2026"));
        assertEquals("2026-03-04", RecognizedDateNormalizer.normalizeDate("03/04/2026"));
    }

    @Test
    void normalizeDate_monthCannotExceed12_swapDay() {
        assertEquals("2026-06-13", RecognizedDateNormalizer.normalizeDate("13/06/2026"));
        assertEquals("2026-06-13", RecognizedDateNormalizer.normalizeDate("13.06.2026"));
        assertEquals("2026-06-13", RecognizedDateNormalizer.normalizeDate("06/13/2026"));
        assertEquals("2026-06-13", RecognizedDateNormalizer.normalizeDate("06.13.2026"));
    }

    @Test
    void normalizeDate_isoPassthrough() {
        assertEquals("2026-04-03", RecognizedDateNormalizer.normalizeDate("2026-04-03"));
        assertEquals("2026-09-03", RecognizedDateNormalizer.normalizeDate("2026-09-03"));
        assertEquals("2026-03-09", RecognizedDateNormalizer.normalizeDate("2026-03-09"));
        assertEquals("2026-02-07", RecognizedDateNormalizer.normalizeDate("2026-02-07"));
    }

    @Test
    void normalizeDate_isoIllegalMonth_swapOrEmpty() {
        assertEquals("2026-12-25", RecognizedDateNormalizer.normalizeDate("2026-25-12"));
        assertEquals("2026-05-13", RecognizedDateNormalizer.normalizeDate("2026-13-05"));
        assertEquals("2026-12-25", RecognizedDateNormalizer.normalizeDate("2026/25/12"));
        assertEquals("", RecognizedDateNormalizer.normalizeDate("2026-32-15"));
        assertEquals("", RecognizedDateNormalizer.normalizeDate("2026-32-11"));
    }

    @Test
    void applyDateWithRaw_rebuildsMonthDayFromSegments() {
        assertEquals("2026-07-02", RecognizedDateNormalizer.applyDateWithRaw("2026-02-07", "07/02/26"));
        assertEquals("2026-09-01", RecognizedDateNormalizer.applyDateWithRaw("2026-01-09", "09/01/2026"));
        assertEquals("2026-09-03", RecognizedDateNormalizer.applyDateWithRaw("2026-09-03", "09/03/2026"));
        assertEquals("2026-12-25", RecognizedDateNormalizer.applyDateWithRaw("2026-25-12", "25/12/2026"));
    }

    @Test
    void applyDateWithRaw_emptyRawFallsBack() {
        assertEquals("2026-02-07", RecognizedDateNormalizer.applyDateWithRaw("2026-02-07", ""));
        assertEquals("2026-12-25", RecognizedDateNormalizer.applyDateWithRaw("2026-25-12", ""));
    }

    @Test
    void isDateFormatInvalid_blocksUnparseable() {
        assertTrue(RecognizedDateNormalizer.isDateFormatInvalid("not-a-date"));
        assertTrue(RecognizedDateNormalizer.isDateFormatInvalid("03/04/2026"));
    }

    @Test
    void isDateFormatInvalid_acceptsCanonical() {
        assertFalse(RecognizedDateNormalizer.isDateFormatInvalid("2026-04-03"));
        assertFalse(RecognizedDateNormalizer.isDateFormatInvalid("2026-12-06"));
    }
}
