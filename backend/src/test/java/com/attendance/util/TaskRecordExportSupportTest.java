package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.entity.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void formatAnomalyDescription_joinsAnomaliesAndUnreadable() {
        JSONObject record = new JSONObject();
        record.put("anomalies", new JSONArray().fluentAdd("missing.Date"));
        record.put("_unreadableFields", new JSONArray().fluentAdd("NOM_PRENOM"));
        String text = TaskRecordExportSupport.formatAnomalyDescription(record);
        assertTrue(text.contains("日期未识别"));
        assertTrue(text.contains("看不清：姓名"));
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
