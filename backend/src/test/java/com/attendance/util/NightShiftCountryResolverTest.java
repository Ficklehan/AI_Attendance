package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NightShiftCountryResolverTest {

    @Test
    void resolvesFromPaysLabel() {
        assertEquals("FR", NightShiftCountryResolver.resolve("France", null));
        assertEquals("DE", NightShiftCountryResolver.resolve("Germany", null));
    }

    @Test
    void resolvesFromCountryCode() {
        assertEquals("FR", NightShiftCountryResolver.resolve("FR", null));
    }

    @Test
    void fallsBackToTaskCountry() {
        assertEquals("NL", NightShiftCountryResolver.resolve(null, "NL"));
        assertEquals("PL", NightShiftCountryResolver.resolve("", "PL"));
    }

    @Test
    void paysTakesPrecedenceOverTaskCountry() {
        assertEquals("FR", NightShiftCountryResolver.resolve("France", "DE"));
    }

    @Test
    void defaultsWhenUnknown() {
        assertEquals("default", NightShiftCountryResolver.resolve(null, null));
        assertEquals("default", NightShiftCountryResolver.resolve("UnknownLand", null));
    }
}
