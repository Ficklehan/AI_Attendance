package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.entity.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRecordExportSupportTest {

    @Test
    void formatWorkHours_computesFromTimes() {
        JSONObject record = new JSONObject();
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "17:30");
        record.put("PAUSE", 60);
        assertEquals("8.50", TaskRecordExportSupport.formatWorkHours(record));
    }

    @Test
    void formatWorkHours_returnsDashForAbsent() {
        JSONObject record = new JSONObject();
        record.put("SmartMark", "未出勤");
        assertEquals("-", TaskRecordExportSupport.formatWorkHours(record));
    }

    @Test
    void formatAnomalyDescription_joinsGroupedSummariesLikeConfirmPage() {
        JSONObject record = new JSONObject();
        record.put("Date", "2024-01-01");
        record.put("NOM_PRENOM", "Alice");
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "17:00");
        record.put("PAUSE", "60");
        record.put("anomalies", new JSONArray().fluentAdd("missing.Observations"));
        record.put("_unreadableFields", new JSONArray().fluentAdd("NOM_PRENOM"));
        String text = TaskRecordExportSupport.formatAnomalyDescription(record);
        assertTrue(text.contains("看不清：姓名"));
        assertTrue(text.contains("\n"));
        assertTrue(text.startsWith("1. "));
        assertFalse(text.contains("日期未识别"));
    }

    @Test
    void formatAnomalyDescription_includesShiftVarianceSentence() {
        JSONObject record = new JSONObject();
        record.put("Date", "2024-01-01");
        record.put("NOM_PRENOM", "Alice");
        record.put("HORAIRES_DU_TRAVAIL", "08:00-17:00");
        record.put("ARRIVEE", "07:30");
        record.put("DEPAR", "18:00");
        record.put("PAUSE", "60");
        String text = TaskRecordExportSupport.formatAnomalyDescription(record);
        assertTrue(text.contains("员工早到"));
        assertTrue(text.contains("晚离开"));
        assertTrue(text.startsWith("1. "));
    }

    @Test
    void formatAnomalyDescription_marksAndManualFlagsFirst() {
        JSONObject record = new JSONObject();
        record.put("SmartMark", "模糊");
        record.put("_manuallyAdded", true);
        record.put("_manualCalibrated", true);
        record.put("Date", "2024-01-01");
        record.put("NOM_PRENOM", "Alice");
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "17:00");
        record.put("PAUSE", "60");
        String text = TaskRecordExportSupport.formatAnomalyDescription(record);
        assertTrue(text.contains("手工补录"));
        assertTrue(text.contains("模糊"));
        assertTrue(text.contains("人工校准"));
        assertTrue(text.indexOf("手工补录") < text.indexOf("模糊"));
    }

    @Test
    void resolvePageNum_readsAliases() {
        JSONObject record = new JSONObject();
        record.put("pageNum", "2");
        assertEquals("2", TaskRecordExportSupport.resolvePageNum(record));
    }

    @Test
    void toExportJson_computesWorkHoursFromTaskRecord() {
        TaskRecord row = new TaskRecord();
        row.setArrival("08:00");
        row.setDeparture("17:30");
        row.setPauseMinutes("60");
        JSONObject json = TaskRecordExportSupport.toExportJson(row);
        assertEquals("8.50", TaskRecordExportSupport.formatWorkHours(json));
        assertEquals("08:00", json.getString("ARRIVEE"));
    }
}
