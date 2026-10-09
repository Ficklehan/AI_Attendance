package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowReadabilitySupportAbsentTest {

    @Test
    void clearsTimesWhenObservationsContainAbsent() {
        JSONObject record = new JSONObject();
        record.put("Observations", "ABSENT");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "18:00");
        record.put("PAUSE", "30");

        assertTrue(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("", record.getString("DEPAR"));
        assertEquals("", record.getString("PAUSE"));
        assertEquals("未出勤", record.getString("Mark"));
    }

    @Test
    void clearsTimesWhenMarkAlreadyAbsentButPunchFilled() {
        JSONObject record = new JSONObject();
        record.put("Observations", "");
        record.put("Mark", "未出勤");
        record.put("ARRIVEE", "20:00");
        record.put("DEPAR", "08:00");
        record.put("PAUSE", "0");

        assertTrue(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("", record.getString("DEPAR"));
        assertEquals("", record.getString("PAUSE"));
        assertEquals("未出勤", record.getString("Mark"));
    }

    @Test
    void leavesNormalRowUntouched() {
        JSONObject record = new JSONObject();
        record.put("Observations", "RAS");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "18:00");
        record.put("PAUSE", "30");

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("09:00", record.getString("ARRIVEE"));
        assertEquals("正常", record.getString("Mark"));
    }

    @Test
    void detectsAbsentInMixedCaseObservations() {
        JSONObject record = new JSONObject();
        record.put("Observations", "Absent - maladie");
        assertTrue(RowReadabilitySupport.observationsIndicateAbsent(record));
        assertTrue(RowReadabilitySupport.isAbsentRow(record));
    }
}
