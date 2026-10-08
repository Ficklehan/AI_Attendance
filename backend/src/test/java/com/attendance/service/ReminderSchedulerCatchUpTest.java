package com.attendance.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReminderSchedulerCatchUpTest {

    @Test
    void resolveUndeliveredBuckets_windowOnlyWhenFullyCoveredBefore() {
        Set<String> delivered = new HashSet<>();
        for (long i = 1; i <= 10; i++) {
            delivered.add(String.valueOf(i));
        }
        List<String> undelivered = ReminderSchedulerService.resolveUndeliveredBuckets(12, delivered);
        assertEquals(Arrays.asList("11", "12"), undelivered);
    }

    @Test
    void resolveUndeliveredBuckets_addsLegacyMarkerWhenOlderDebtExists() {
        Set<String> delivered = new HashSet<>();
        delivered.add("600");
        List<String> undelivered = ReminderSchedulerService.resolveUndeliveredBuckets(600, delivered);
        assertTrue(undelivered.contains("legacy_before_101"));
        assertFalse(undelivered.contains("600"));
        assertTrue(undelivered.contains("101"));
        assertTrue(undelivered.contains("599"));
    }

    @Test
    void resolveUndeliveredBuckets_skipsLegacyWhenMarkerPresent() {
        Set<String> delivered = new HashSet<>();
        delivered.add("legacy_before_101");
        for (long i = 101; i <= 600; i++) {
            delivered.add(String.valueOf(i));
        }
        assertEquals(Collections.emptyList(), ReminderSchedulerService.resolveUndeliveredBuckets(600, delivered));
    }

    @Test
    void resolveUndeliveredBuckets_emptyWhenAllDelivered() {
        Set<String> delivered = new HashSet<>();
        delivered.add("1");
        delivered.add("2");
        delivered.add("3");
        assertEquals(Collections.emptyList(), ReminderSchedulerService.resolveUndeliveredBuckets(3, delivered));
    }
}
