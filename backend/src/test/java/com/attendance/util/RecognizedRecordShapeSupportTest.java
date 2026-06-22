package com.attendance.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognizedRecordShapeSupportTest {

    @Test
    void repairStickyRowBoundaries_splits_false_before_next_row() {
        String broken = "50ITALIAMILANO20260617HASSANARMANTempus120021001220false"
                + "[\"53\",\"ITALIA\",\"MILANO\",\"2026-06-17\",\"ALI MUHAMMAD ASGHAR\",\"Tempus\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"false\",\"\"]";
        String repaired = RecognizedRecordShapeSupport.repairStickyRowBoundaries(broken);
        assertTrue(repaired.contains("[\"50\""));
        assertTrue(repaired.contains("[\"53\""));
    }

    @Test
    void expandMergedRowArrays_splits_sixteen_element_row() {
        JSONArray merged = JSON.parseArray(
                "[\"50ITALIAMILANO20260617HASSANARMANTempus120021001220false\",\"53\",\"ITALIA\",\"MILANO\","
                        + "\"2026-06-17\",\"ALI MUHAMMAD ASGHAR\",\"Tempus\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"false\",\"\"]");
        List<JSONArray> rows = RecognizedRecordShapeSupport.expandMergedRowArrays(merged);
        assertEquals(2, rows.size());
        assertEquals("50", rows.get(0).getString(0));
        assertEquals("53", rows.get(1).getString(0));
        assertEquals("ITALIA", rows.get(1).getString(1));
    }

    @Test
    void trySplitMergedBlob_parses_user_example() {
        JSONArray row = RecognizedRecordShapeSupport.trySplitMergedBlob(
                "50ITALIAMILANO20260617HASSANARMANTempus120021001220false");
        assertNotNull(row);
        assertEquals(15, row.size());
        assertEquals("50", row.getString(0));
        assertEquals("ITALIA", row.getString(1));
        assertEquals("MILANO", row.getString(2));
        assertEquals("2026-06-17", row.getString(3));
        assertEquals("HASSANARMAN", row.getString(4));
        assertEquals("Tempus", row.getString(5));
        assertEquals("12:00-21:00", row.getString(6));
    }

    @Test
    void looksLikeMergedBlob_detects_concatenated_values() {
        assertTrue(RecognizedRecordShapeSupport.looksLikeMergedBlob(
                "50ITALIAMILANO20260617HASSANARMANTempus120021001220false"));
        assertFalse(RecognizedRecordShapeSupport.looksLikeMergedBlob("53"));
    }

    @Test
    void malformedRatio_counts_flagged_rows() {
        com.alibaba.fastjson.JSONObject a = new com.alibaba.fastjson.JSONObject();
        RecognizedRecordShapeSupport.markMalformed(a, "test");
        com.alibaba.fastjson.JSONObject b = new com.alibaba.fastjson.JSONObject();
        assertEquals(0.5d, RecognizedRecordShapeSupport.malformedRatio(
                java.util.Arrays.asList(a, b)), 0.001d);
    }
}
