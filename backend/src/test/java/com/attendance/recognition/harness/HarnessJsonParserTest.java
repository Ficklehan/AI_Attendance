package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.recognition.AttendanceRuleEngine;
import com.attendance.recognition.dto.HarnessSheet;
import com.attendance.recognition.dto.OcrRawResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessJsonParserTest {

    @Test
    void parseSheetRowsWithConfidenceAndBbox() {
        String raw = "{\"page_num\":\"1/1\",\"warehouse\":\"MARLY\",\"date_raw\":\"09/07/2026\","
                + "\"rows\":[{\"row_no\":\"2\",\"name\":{\"text\":\"SISSOKO\",\"confidence\":0.94,"
                + "\"bbox\":[50,40,200,70]},\"name_crossed\":false,"
                + "\"arrivo\":{\"text\":\"10:00\",\"confidence\":0.96,\"bbox\":[410,40,480,70],\"empty\":false},"
                + "\"uscita\":{\"text\":\"19:00\",\"confidence\":0.72,\"bbox\":[490,40,560,70],\"empty\":false},"
                + "\"pausa\":{\"text\":\"0\",\"confidence\":0.99}}]}";
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        assertEquals(1, sheet.rows.size());
        assertEquals("MARLY", sheet.warehouse);
        assertEquals("09/07/2026", sheet.dateRaw);
        assertEquals("SISSOKO", sheet.rows.get(0).name.text);
        assertEquals(0.72d, sheet.rows.get(0).uscita.confidence, 0.001d);
        assertEquals(410, sheet.rows.get(0).arrivo.bbox.x);
        assertEquals(70, sheet.rows.get(0).arrivo.bbox.w);

        RecognitionProperties props = new RecognitionProperties();
        AttendanceRuleEngine engine = new AttendanceRuleEngine(props);
        OcrRawResult mapped = HarnessSheetMapper.toRaw(sheet.rows.get(0), sheet, 0.85d);
        JSONObject rec = engine.apply(mapped, "FR", NightShiftConfigDTO.defaults());
        assertEquals("10:00", rec.getString("ARRIVEE"));
        assertEquals("19:00", rec.getString("DEPAR"));
        assertTrue(rec.getString("Mark").contains("模糊"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
        assertEquals(0.72d, rec.getDoubleValue("_uscitaConfidence"), 0.001d);
        assertEquals("2026-09-07", rec.getString("Date"));
    }

    @Test
    void emptyClockCellsAreAbsent() {
        String raw = "{\"date_raw\":\"09/07/2026\",\"rows\":[{\"row_no\":\"8\",\"name\":{\"text\":\"KHONDOKER\"},"
                + "\"arrivo\":{\"text\":\"\",\"empty\":true,\"confidence\":1},"
                + "\"uscita\":{\"text\":\"\",\"empty\":true,\"confidence\":1}}]}";
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        AttendanceRuleEngine engine = new AttendanceRuleEngine(new RecognitionProperties());
        JSONObject rec = engine.apply(HarnessSheetMapper.toRaw(sheet.rows.get(0), sheet, 0.85d),
                "IT", NightShiftConfigDTO.defaults());
        assertEquals("未出勤", rec.getString("Mark"));
        assertEquals("", rec.getString("ARRIVEE"));
        assertFalse(rec.getBooleanValue("isDeleted"));
    }

    @Test
    void nameCrossedWithoutClocksIsDeleted() {
        String raw = "{\"rows\":[{\"row_no\":\"13\",\"name\":{\"text\":\"UDDIN\"},\"name_crossed\":true,"
                + "\"arrivo\":{\"empty\":true},\"uscita\":{\"empty\":true}}]}";
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        AttendanceRuleEngine engine = new AttendanceRuleEngine(new RecognitionProperties());
        JSONObject rec = engine.apply(HarnessSheetMapper.toRaw(sheet.rows.get(0), sheet, 0.85d),
                "IT", NightShiftConfigDTO.defaults());
        assertTrue(rec.getBooleanValue("isDeleted"));
        assertEquals("未出勤", rec.getString("Mark"));
    }

    @Test
    void blankLowConfClockIsBlurNotAbsent() {
        String raw = "{\"rows\":[{\"row_no\":\"3\",\"name\":{\"text\":\"MESSY\"},"
                + "\"arrivo\":{\"text\":\"\",\"confidence\":0.4,\"empty\":false},"
                + "\"uscita\":{\"text\":\"\",\"empty\":true,\"confidence\":1}}]}";
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        AttendanceRuleEngine engine = new AttendanceRuleEngine(new RecognitionProperties());
        JSONObject rec = engine.apply(HarnessSheetMapper.toRaw(sheet.rows.get(0), sheet, 0.85d),
                "IT", NightShiftConfigDTO.defaults());
        assertTrue(rec.getString("Mark").contains("模糊"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
        assertEquals("", rec.getString("ARRIVEE"));
    }

    @Test
    void buildTaskAsksForRowsJsonNotMark() {
        String task = HarnessHeadlessClient.buildTask(java.nio.file.Paths.get("/tmp/x.png"), "FR", null);
        assertTrue(task.contains("rows"));
        assertTrue(task.contains("Do not output Mark"));
        assertTrue(task.contains("ocr_image always"));
    }

    @Test
    void parseRunReadsFinalEvent() {
        String stdout = "{\"type\":\"session\",\"sessionId\":\"s1\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"crop_zoom\"}\n"
                + "{\"type\":\"final\",\"text\":\"{\\\"rows\\\":[]}\"}\n";
        HarnessHeadlessClient.RunResult run = HarnessHeadlessClient.parseRun(stdout, "", 0);
        assertEquals("s1", run.sessionId);
        assertEquals(1, run.toolCalls);
        assertTrue(run.answer.contains("rows"));
    }
}
