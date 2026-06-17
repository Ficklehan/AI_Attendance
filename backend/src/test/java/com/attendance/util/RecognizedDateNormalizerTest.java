package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecognizedDateNormalizerTest {

    @Test
    void normalizeDate_unambiguousDayFirst() {
        assertEquals("2026-06-12", RecognizedDateNormalizer.normalizeDate("12/06/2026"));
        assertEquals("2026-06-12", RecognizedDateNormalizer.normalizeDate("12.06.2026"));
    }

    @Test
    void normalizeDate_keepsAmbiguousSlashFormat() {
        assertEquals("03/04/2026", RecognizedDateNormalizer.normalizeDate("03/04/2026"));
    }

    @Test
    void normalizeDate_isoPassthrough() {
        assertEquals("2026-04-03", RecognizedDateNormalizer.normalizeDate("2026-04-03"));
    }

    @Test
    void isDateFormatInvalid_blocksUnparseable() {
        assertTrue(RecognizedDateNormalizer.isDateFormatInvalid("not-a-date"));
        assertTrue(RecognizedDateNormalizer.isDateFormatInvalid("03/04/2026"));
    }

    @Test
    void isDateFormatInvalid_acceptsCanonical() {
        assertFalse(RecognizedDateNormalizer.isDateFormatInvalid("2026-04-03"));
        assertFalse(RecognizedDateNormalizer.isDateFormatInvalid("2026-06-12"));
    }
}
