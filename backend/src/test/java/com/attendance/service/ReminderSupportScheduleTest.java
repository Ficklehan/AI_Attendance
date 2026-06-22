package com.attendance.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderSupportScheduleTest {

    @Test
    void computeDueAtForPeriod_minuteInterval() {
        LocalDateTime entered = LocalDateTime.of(2026, 6, 17, 10, 0, 0);
        LocalDateTime due = ReminderSupport.computeDueAtForPeriod(
                entered, 1, new BigDecimal("0.5"), "minute", null);
        assertEquals(LocalDateTime.of(2026, 6, 17, 10, 0, 30), due);
    }

    @Test
    void computeDueAtForPeriod_dayIntervalBeforeScheduleHour() {
        LocalDateTime entered = LocalDateTime.of(2026, 6, 16, 8, 0, 0);
        LocalDateTime due = ReminderSupport.computeDueAtForPeriod(
                entered, 1, new BigDecimal("1"), "day", 9);
        assertEquals(LocalDateTime.of(2026, 6, 17, 9, 0, 0), due);
    }

    @Test
    void computeDueAtForPeriod_dayIntervalAfterScheduleHour() {
        LocalDateTime entered = LocalDateTime.of(2026, 6, 16, 14, 0, 0);
        LocalDateTime due = ReminderSupport.computeDueAtForPeriod(
                entered, 1, new BigDecimal("1"), "day", 9);
        assertEquals(LocalDateTime.of(2026, 6, 18, 9, 0, 0), due);
    }

    @Test
    void resolveNextPeriodToSchedule_skipsDeliveredPeriods() {
        LocalDateTime entered = LocalDateTime.of(2026, 6, 15, 10, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 12, 0, 0);
        Set<Long> delivered = new HashSet<>();
        delivered.add(1L);
        long next = ReminderSupport.resolveNextPeriodToSchedule(
                entered,
                now,
                new BigDecimal("1"),
                "day",
                delivered::contains);
        assertEquals(2L, next);
    }

    @Test
    void resolveNextPeriodToSchedule_futureFirstPeriod() {
        LocalDateTime entered = LocalDateTime.of(2026, 6, 17, 16, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 16, 10, 0);
        long next = ReminderSupport.resolveNextPeriodToSchedule(
                entered,
                now,
                new BigDecimal("1"),
                "day",
                period -> false);
        assertEquals(1L, next);
        LocalDateTime due = ReminderSupport.computeDueAtForPeriod(
                entered, next, new BigDecimal("1"), "day", 9);
        assertTrue(due.isAfter(now));
    }

    @Test
    void isScheduleTimeReached_dayUnitBeforeHour() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 8, 30, 0);
        assertFalse(ReminderSupport.isScheduleTimeReached("day", 9, now));
    }

    @Test
    void isScheduleTimeReached_dayUnitAtHour() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 9, 0, 0);
        assertTrue(ReminderSupport.isScheduleTimeReached("day", 9, now));
    }

    @Test
    void computePeriodIndex_catchUpTenDays() {
        LocalDateTime entered = LocalDateTime.of(2026, 6, 8, 10, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 10, 0, 0);
        long intervalMs = ReminderSupport.intervalToMillis(new BigDecimal("1"), "day");
        assertEquals(10L, ReminderSupport.computePeriodIndex(entered, now, intervalMs));
    }
}
