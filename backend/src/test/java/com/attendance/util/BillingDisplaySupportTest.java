package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingDisplaySupportTest {

    @Test
    void resolveCountryLabel_fromCode() {
        assertEquals("Netherlands", BillingDisplaySupport.resolveCountryLabel(null, "NL"));
        assertEquals("France", BillingDisplaySupport.resolveCountryLabel("", "FR"));
    }

    @Test
    void resolveTextLabel_humanizesKey() {
        assertEquals("Manpower", BillingDisplaySupport.resolveTextLabel(null, "MANPOWER"));
        assertEquals("Manpower", BillingDisplaySupport.resolveTextLabel("MANPOWER", "MANPOWER"));
    }

    @Test
    void resolveTextLabel_keepsShortWarehouseCode() {
        assertEquals("AMS", BillingDisplaySupport.resolveTextLabel(null, "AMS"));
        assertEquals("PAR", BillingDisplaySupport.resolveTextLabel("PAR", "PAR"));
    }
}
