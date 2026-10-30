package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionStatusMarkTest {

    @Test
    void emptyClocksDoNotMakeAbsentWhenMarkIsNormal() {
        JSONObject record = new JSONObject();
        record.put("Mark", "正常");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertFalse(RecognitionStatusMark.isAbsentStatus(record));
    }

    @Test
    void blurWinsOverAbsentAndEmptyClocks() {
        JSONObject record = new JSONObject();
        record.put("Mark", "模糊;未出勤");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertTrue(RecognitionStatusMark.containsBlur(record.getString("Mark")));
        assertFalse(RecognitionStatusMark.isAbsentStatus(record));
    }

    @Test
    void absentMarkStaysWhenClocksEmpty() {
        JSONObject record = new JSONObject();
        record.put("Mark", "未出勤");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertTrue(RecognitionStatusMark.isAbsentStatus(record));
    }

    @Test
    void correctedClocksClearModelAbsentStatus() {
        JSONObject record = new JSONObject();
        record.put("Mark", "未出勤");
        record.put("SmartMark", "未出勤");
        record.put("ARRIVEE", "10:00");
        record.put("DEPAR", "19:00");
        assertFalse(RecognitionStatusMark.isAbsentStatus(record));
    }

    @Test
    void missingPunchTokensAreDetected() {
        assertTrue(RecognitionStatusMark.containsMissingPunch("到达缺位"));
        assertTrue(RecognitionStatusMark.containsMissingPunch("离开缺卡"));
        assertTrue(RecognitionStatusMark.containsMissingPunch("模糊;到达缺卡"));
        assertFalse(RecognitionStatusMark.containsMissingPunch("正常"));
    }

    @Test
    void canonicalizesLegacyQueweiToQueka() {
        assertEquals("缺卡", RecognitionStatusMark.canonicalizeToken("缺位"));
        assertEquals("缺卡", RecognitionStatusMark.canonicalizeToken("到达缺位"));
        assertEquals("缺卡", RecognitionStatusMark.canonicalizeToken("离开缺位"));
        assertEquals("缺卡", RecognitionStatusMark.canonicalizeToken("到达缺卡"));
        assertEquals("缺卡", RecognitionStatusMark.canonicalizeToken("缺卡"));
        assertEquals("模糊", RecognitionStatusMark.canonicalizeToken("模糊"));
    }
}
