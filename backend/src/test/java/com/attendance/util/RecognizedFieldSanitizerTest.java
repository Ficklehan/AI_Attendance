package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognizedFieldSanitizerTest {

    @Test
    void annotate_moves_unreadable_tokens_to_metadata() {
        JSONObject record = new JSONObject();
        record.put("NO", "1001");
        record.put("NOM_PRENOM", "???");
        record.put("ARRIVEE", "illegible");
        record.put("DEPAR", "17:00");
        record.put("SmartMark", "正常");

        RecognizedFieldSanitizer.annotateAndSanitizeRecord(record);

        assertEquals("", record.getString("NOM_PRENOM"));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("17:00", record.getString("DEPAR"));
        assertTrue(record.getJSONArray("_unreadableFields").contains("NOM_PRENOM"));
        assertTrue(record.getJSONArray("_unreadableFields").contains("ARRIVEE"));
        assertTrue(record.getString("SmartMark").contains("模糊"));
    }

    @Test
    void sanitize_on_confirm_strips_metadata() {
        JSONObject record = new JSONObject();
        record.put("NO", "???");
        com.alibaba.fastjson.JSONArray unreadable = new com.alibaba.fastjson.JSONArray();
        unreadable.add("NO");
        record.put("_unreadableFields", unreadable);

        RecognizedFieldSanitizer.sanitizeRecordPlaceholders(record);

        assertEquals("", record.getString("NO"));
        assertFalse(record.containsKey("_unreadableFields"));
    }

    @Test
    void annotate_sanitizes_unreadable_pause() {
        JSONObject record = new JSONObject();
        record.put("NO", "1");
        record.put("PAUSE", "???");
        record.put("SmartMark", "正常");

        RecognizedFieldSanitizer.annotateAndSanitizeRecord(record);

        assertEquals("", record.getString("PAUSE"));
        assertTrue(record.getJSONArray("_unreadableFields").contains("PAUSE"));
    }
}
