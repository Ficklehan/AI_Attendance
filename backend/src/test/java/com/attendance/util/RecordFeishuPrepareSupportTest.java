package com.attendance.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordFeishuPrepareSupportTest {

    @Test
    void buildsDatetimeAndWorkHoursOnConfirm() {
        Map<String, Object> record = new HashMap<>();
        record.put("Date", "2026-06-12");
        record.put("ARRIVEE", "8:30");
        record.put("DEPAR", "17:30");
        record.put("PAUSE", 30);

        RecordFeishuPrepareSupport.prepareRecord(record);

        assertEquals("2026-06-12 08:30", record.get("ARRIVEE_DATETIME"));
        assertEquals("2026-06-12 17:30", record.get("DEPAR_DATETIME"));
        assertEquals(8.0, record.get("WorkHours"));
    }

    @Test
    void nightShiftUsesNextDayDepartureDate() {
        Map<String, Object> record = new HashMap<>();
        record.put("Date", "2026-06-12");
        record.put("ARRIVEE", "22:00");
        record.put("DEPAR", "07:00");
        record.put("PAUSE", 0);

        RecordFeishuPrepareSupport.prepareRecord(record);

        assertEquals("2026-06-12 22:00", record.get("ARRIVEE_DATETIME"));
        assertEquals("2026-06-13 07:00", record.get("DEPAR_DATETIME"));
        assertEquals(9.0, record.get("WorkHours"));
    }

    @Test
    void absentRowSkipsWorkHours() {
        Map<String, Object> record = new HashMap<>();
        record.put("Date", "2026-06-12");
        record.put("ARRIVEE", "08:30");
        record.put("DEPAR", "17:30");
        record.put("SmartMark", "未出勤");

        RecordFeishuPrepareSupport.prepareRecord(record);

        assertFalse(record.containsKey("WorkHours"));
    }

    @Test
    void normalizeTimeAcceptsCommonFormats() {
        assertEquals("08:30", RecordFeishuPrepareSupport.normalizeTime("8:30"));
        assertEquals("08:30", RecordFeishuPrepareSupport.normalizeTime("0830"));
    }
}
