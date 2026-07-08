package com.attendance.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryCatalogTest {

    @Test
    void resolveGlobalDefaultCountryUsesFranceForDefault() {
        assertEquals("FR", CountryCatalog.resolveGlobalDefaultCountry(null));
        assertEquals("FR", CountryCatalog.resolveGlobalDefaultCountry(""));
        assertEquals("FR", CountryCatalog.resolveGlobalDefaultCountry("default"));
        assertEquals("FR", CountryCatalog.resolveGlobalDefaultCountry("DEFAULT"));
        assertEquals("DE", CountryCatalog.resolveGlobalDefaultCountry("DE"));
    }

    @Test
    void defaultPaysLabelForDefaultCountryIsFrance() {
        assertEquals("France", CountryCatalog.defaultPaysLabel("default"));
        assertEquals("France", CountryCatalog.defaultPaysLabel(null));
    }

    @Test
    void resolveLegacyAliases() {
        assertEquals("IT", CountryCatalog.resolveCountryCodeFromPays("ITALIA"));
        assertEquals("IT", CountryCatalog.resolveCountryCodeFromPays("ITA"));
        assertEquals("CN", CountryCatalog.resolveCountryCodeFromPays("CHINA"));
        assertEquals("CN", CountryCatalog.resolveCountryCodeFromPays("China"));
        assertEquals("FR", CountryCatalog.resolveCountryCodeFromPays("France"));
        assertEquals("FR", CountryCatalog.resolveCountryCodeFromPays("FRANCE"));
        assertEquals("FR", CountryCatalog.resolveCountryCodeFromPays("法国"));
        assertEquals("IT", CountryCatalog.resolveCountryCodeFromPays("意大利"));
        assertEquals("NL", CountryCatalog.resolveCountryCodeFromPays("Netherlands"));
        assertEquals("NL", CountryCatalog.resolveCountryCodeFromPays("NL"));
        assertEquals("NL", CountryCatalog.resolveCountryCodeFromPays("荷兰"));
    }

    @Test
    void normalizeCountryKeyPrefersCatalogCode() {
        assertEquals("IT", CountryCatalog.normalizeCountryKey("ITALIA"));
        assertEquals("CN", CountryCatalog.normalizeCountryKey("China"));
    }

    @Test
    void expandMatchTokensIncludesLegacyAliases() {
        List<String> tokens = CountryCatalog.expandMatchTokens(Arrays.asList("CN", "IT"));
        assertTrue(tokens.contains("CN"));
        assertTrue(tokens.contains("CHINA"));
        assertTrue(tokens.contains("China"));
        assertTrue(tokens.contains("IT"));
        assertTrue(tokens.contains("ITALIA"));
        assertTrue(tokens.contains("ITA"));
        assertTrue(tokens.contains("Italy"));
    }
}
