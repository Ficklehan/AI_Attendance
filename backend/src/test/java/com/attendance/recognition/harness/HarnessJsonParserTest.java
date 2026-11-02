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
    void parseSheetToleratesStringBooleansAndCellShapedHeaders() {
        String raw = "{\"page_num\":{\"text\":\"1/1\",\"empty\":false},"
                + "\"warehouse\":{\"text\":\"roma\"},\"date_raw\":{\"text\":\"29/10/2026\"},"
                + "\"rows\":[{\"row_no\":\"1\",\"name\":{\"text\":\"GONZALEZ\"},"
                + "\"name_crossed\":\"false\",\"name_has_replacement\":0,"
                + "\"arrivo\":{\"text\":\"10:00\",\"empty\":\"false\"},"
                + "\"uscita\":{\"text\":\"\",\"empty\":\"true\"}}]}";
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        assertEquals("1/1", sheet.pageNum);
        assertEquals("roma", sheet.warehouse);
        assertEquals("29/10/2026", sheet.dateRaw);
        assertEquals(1, sheet.rows.size());
        assertFalse(sheet.rows.get(0).nameCrossed);
        assertFalse(sheet.rows.get(0).nameHasReplacement);
        assertEquals("10:00", sheet.rows.get(0).arrivo.text);
        assertEquals(Boolean.FALSE, sheet.rows.get(0).arrivo.empty);
        assertEquals(Boolean.TRUE, sheet.rows.get(0).uscita.empty);
    }

    @Test
    void buildTaskAsksForRowsJsonNotMark() {
        String task = HarnessHeadlessClient.buildTask(java.nio.file.Paths.get("/tmp/x.png"), "FR", null);
        assertTrue(task.contains("rows"));
        assertTrue(task.contains("No Mark"));
        assertTrue(task.contains("vision_ground"));
        assertTrue(task.contains("crop_zoom"));
        assertTrue(task.contains("sheet false"));
        assertTrue(task.contains("vision_describe"));
        assertTrue(task.contains("PIXEL ISOLATION"));
        assertTrue(task.contains("never vision_describe the original page"));
        assertTrue(task.contains("First output MUST be vision_ground"));
        assertTrue(task.contains("Do not call ocr_image"));
        assertTrue(task.contains("json false"));
        assertTrue(task.contains("Locate these table columns"));
        assertFalse(task.contains("json true"));
        assertFalse(task.contains("Use skill attendance-sheet"));
        assertFalse(task.contains("Call vision_describe exactly once on that image path"));
        assertFalse(task.contains("First output MUST be a vision_describe"));
        assertFalse(task.contains("ok:false"));
        assertFalse(task.contains("ocr_image always"));
    }

    @Test
    void buildTaskUsesItalianClockHeaders() {
        String task = HarnessHeadlessClient.buildTask(java.nio.file.Paths.get("/tmp/x.png"), "IT", null);
        assertTrue(task.contains("Arrivo"));
        assertTrue(task.contains("Uscita"));
        assertTrue(task.contains("PIXEL ISOLATION"));
    }

    @Test
    void parseRunReadsFinalEventAndToolNames() {
        String stdout = "{\"type\":\"session\",\"sessionId\":\"s1\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"vision_ground\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"crop_zoom\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"ocr_image\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"ocr_image\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"ocr_image\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"vision_describe\"}\n"
                + "{\"type\":\"tool_call\",\"name\":\"vision_describe\"}\n"
                + "{\"type\":\"tool_result\"}\n"
                + "{\"type\":\"final\",\"text\":\"{\\\"rows\\\":[]}\"}\n";
        HarnessHeadlessClient.RunResult run = HarnessHeadlessClient.parseRun(stdout, "", 0);
        assertEquals("s1", run.sessionId);
        assertEquals(8, run.toolCalls);
        assertEquals(1, HarnessHeadlessClient.countTool(run.toolNames, "vision_ground"));
        assertEquals(1, HarnessHeadlessClient.countTool(run.toolNames, "crop_zoom"));
        assertEquals(3, HarnessHeadlessClient.countTool(run.toolNames, "ocr_image"));
        assertEquals(2, HarnessHeadlessClient.countTool(run.toolNames, "vision_describe"));
        assertTrue(run.answer.contains("rows"));
    }

    @Test
    void parseRunSalvagesRowsFromToolResultWhenAnswerEmpty() {
        String stdout = "{\"type\":\"session\",\"sessionId\":\"s2\"}\n"
                + "{\"type\":\"tool_call\",\"tool\":\"vision_describe\"}\n"
                + "{\"type\":\"tool_result\",\"callId\":\"c1\",\"status\":\"completed\","
                + "\"result\":\"{\\\"page_num\\\":\\\"1/1\\\",\\\"warehouse\\\":\\\"roma\\\","
                + "\\\"date_raw\\\":\\\"10/29/2026\\\",\\\"rows\\\":[{\\\"row_no\\\":\\\"1\\\","
                + "\\\"name\\\":{\\\"text\\\":\\\"GONZALEZ\\\"},\\\"arrivo\\\":{\\\"text\\\":\\\"10:00\\\"},"
                + "\\\"uscita\\\":{\\\"text\\\":\\\"19:00\\\"}}]}\"}\n"
                + "{\"type\":\"text\",\"text\":\"{\\\"page_num\\\":\\\"1\\\",\\\"warehouse\\\":\\\"\\\","
                + "\\\"date_raw\\\":\\\"\\\",\\\"rows\\\":[]}\"}\n";
        HarnessHeadlessClient.RunResult run = HarnessHeadlessClient.parseRun(stdout, "", 0);
        assertTrue(run.salvagedFromToolResult);
        assertTrue(run.toolResultHasRows);
        HarnessSheet sheet = HarnessJsonParser.parseSheet(run.answer);
        assertEquals(1, sheet.rows.size());
        assertEquals("GONZALEZ", sheet.rows.get(0).name.text);
        assertEquals("roma", sheet.warehouse);
    }

    @Test
    void mapperDoesNotCopyShiftIntoClocks() {
        String raw = "{\"rows\":[{\"row_no\":\"1\",\"name\":{\"text\":\"A\"},"
                + "\"shift\":{\"text\":\"08:00-16:00\"},"
                + "\"arrivo\":{\"text\":\"\",\"empty\":true,\"confidence\":1},"
                + "\"uscita\":{\"text\":\"\",\"empty\":true,\"confidence\":1}}]}";
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        AttendanceRuleEngine engine = new AttendanceRuleEngine(new RecognitionProperties());
        JSONObject rec = engine.apply(HarnessSheetMapper.toRaw(sheet.rows.get(0), sheet, 0.85d),
                "IT", NightShiftConfigDTO.defaults());
        assertEquals("08:00-16:00", rec.getString("HORAIRES_DU_TRAVAIL"));
        assertEquals("", rec.getString("ARRIVEE"));
        assertEquals("", rec.getString("DEPAR"));
        assertEquals("未出勤", rec.getString("Mark"));
    }
}
