package com.attendance.util;

import com.attendance.dto.NightShiftConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightShiftMarkSupportTest {

    private static NightShiftConfigDTO defaultRules() {
        return NightShiftConfigDTO.defaults();
    }

    @Test
    void detectsNightByLateArrival() {
        assertTrue(NightShiftMarkSupport.shouldMarkNightShift("22:00", "07:30", null, defaultRules()));
    }

    @Test
    void detectsNightByEarlyDeparture() {
        assertTrue(NightShiftMarkSupport.shouldMarkNightShift("15:00", "00:03", null, defaultRules()));
    }

    @Test
    void detectsNightByScheduleRange() {
        assertTrue(NightShiftMarkSupport.shouldMarkNightShift("22:00", "23:10", "15:00-00:00", defaultRules()));
        assertTrue(NightShiftMarkSupport.shouldMarkNightShift(null, null, "22:00-7:00", defaultRules()));
    }

    @Test
    void dayShiftNotMarked() {
        assertFalse(NightShiftMarkSupport.shouldMarkNightShift("13:00", "22:00", "13:00-22:00", defaultRules()));
        assertFalse(NightShiftMarkSupport.shouldMarkNightShift("08:00", "17:00", "08:00-17:00", defaultRules()));
    }

    @Test
    void dayTimesOverrideNightSchedule() {
        assertFalse(NightShiftMarkSupport.shouldMarkNightShift("08:00", "17:00", "22:00-07:00", defaultRules()));
    }

    @Test
    void respectsCustomStartTime() {
        NightShiftConfigDTO rules = NightShiftConfigDTO.defaults();
        rules.setStartTime("21:00");
        assertFalse(NightShiftMarkSupport.shouldMarkNightShift("20:30", "23:00", null, rules));
        assertTrue(NightShiftMarkSupport.shouldMarkNightShift("21:00", "23:00", null, rules));
    }

    @Test
    void respectsDisabledCrossMidnight() {
        NightShiftConfigDTO rules = NightShiftConfigDTO.defaults();
        rules.setCrossMidnight(false);
        assertFalse(NightShiftMarkSupport.shouldMarkNightShift("18:00", "17:00", null, rules));
    }
}
