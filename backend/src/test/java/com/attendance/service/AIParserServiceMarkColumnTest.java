package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIParserServiceMarkColumnTest {

    @Test
    void movesRemarkTextFromMarkToEmptyObservations() {
        JSONObject record = new JSONObject();
        record.put("Observations", "");
        record.put("Mark", "请假半天");

        AIParserService.salvageMisplacedMarkColumn(record);

        assertEquals("请假半天", record.getString("Observations"));
        assertEquals("", record.getString("Mark"));
    }

    @Test
    void keepsClassificationTokenAndSplitsFreeText() {
        JSONObject record = new JSONObject();
        record.put("Observations", "");
        record.put("Mark", "正常;身体不适");

        AIParserService.salvageMisplacedMarkColumn(record);

        assertEquals("正常", record.getString("Mark"));
        assertEquals("身体不适", record.getString("Observations"));
    }

    @Test
    void doesNotOverwriteExistingObservationsButStillCleansMark() {
        JSONObject record = new JSONObject();
        record.put("Observations", "原始备注");
        record.put("Mark", "手写;迟到说明");

        AIParserService.salvageMisplacedMarkColumn(record);

        assertEquals("原始备注", record.getString("Observations"));
        assertEquals("手写", record.getString("Mark"));
    }

    @Test
    void leavesPureClassificationMarkUntouched() {
        JSONObject record = new JSONObject();
        record.put("Observations", "");
        record.put("Mark", "手写;未出勤");

        AIParserService.salvageMisplacedMarkColumn(record);

        assertEquals("手写;未出勤", record.getString("Mark"));
        assertTrue(record.getString("Observations").isEmpty());
    }
}
