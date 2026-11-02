package com.attendance.recognition;

import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.recognition.dto.PageLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredRecognitionPipelineTest {

    @Test
    void groundFailureYieldsBlurNotAbsent() {
        AttendanceRuleEngine engine = new AttendanceRuleEngine(new RecognitionProperties());
        PageLayout layout = new PageLayout();
        layout.warehouse = "WH1";
        layout.dateRaw = "30/10/2026";
        JSONObject rec = StructuredRecognitionPipeline.structureFailed(
                engine, "IT", layout, NightShiftConfigDTO.defaults());
        assertTrue(rec.getBooleanValue("_structureFailed"));
        assertEquals("ground_failed", rec.getString("_reviewReason"));
        assertTrue(rec.getString("Mark").contains("模糊"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
        assertEquals("", rec.getString("ARRIVEE"));
        assertEquals("", rec.getString("DEPAR"));
        assertEquals("WH1", rec.getString("Entrepot"));
    }

    @Test
    void legacyPipelineIsNotOcrPrimary() {
        RecognitionProperties props = new RecognitionProperties();
        props.setPipeline("legacy");
        assertFalse(props.isOcrPrimary());
        props.setPipeline("ocr_primary");
        assertTrue(props.isOcrPrimary());
    }

    @Test
    void dropTrailingEmptyRemovesBlankTail() {
        JSONObject person = new JSONObject();
        person.put("NOM_PRENOM", "SISSOKO");
        person.put("ARRIVEE", "10:00");
        JSONObject blank = new JSONObject();
        java.util.List<JSONObject> rows = new java.util.ArrayList<JSONObject>();
        rows.add(person);
        rows.add(blank);
        rows.add(new JSONObject());
        StructuredRecognitionPipeline.dropTrailingEmpty(rows);
        assertEquals(1, rows.size());
        assertEquals("SISSOKO", rows.get(0).getString("NOM_PRENOM"));
    }

    @Test
    void dropHeaderRowsRemovesColumnTitle() {
        JSONObject header = new JSONObject();
        header.put("NOM_PRENOM", "Cognome Nome");
        JSONObject person = new JSONObject();
        person.put("NOM_PRENOM", "GONZALEZ");
        person.put("ARRIVEE", "10:00");
        java.util.List<JSONObject> rows = new java.util.ArrayList<JSONObject>();
        rows.add(header);
        rows.add(person);
        StructuredRecognitionPipeline.dropHeaderRows(rows);
        assertEquals(1, rows.size());
        assertEquals("GONZALEZ", rows.get(0).getString("NOM_PRENOM"));
        assertTrue(StructuredRecognitionPipeline.isHeaderName("Nome e cognome"));
    }

    @Test
    void harnessDefaultsProfileAndTimeout() {
        RecognitionProperties.Harness harness = new RecognitionProperties().getHarness();
        assertTrue(harness.isEnabled());
        assertEquals("attendance", harness.getProfile());
        assertEquals(600000, harness.getExtractTimeoutMs());
    }

    @Test
    void pageFileNameKeepsJpeg() {
        assertEquals("page.jpg", StructuredRecognitionPipeline.pageFileName("roma-foglio-20261028.jpg"));
        assertEquals("page.png", StructuredRecognitionPipeline.pageFileName("sheet.png"));
        assertEquals("page.png", StructuredRecognitionPipeline.pageFileName(null));
    }

    @Test
    void dropBlankNameAndClocksRemovesEmptyRows() {
        JSONObject person = new JSONObject();
        person.put("NOM_PRENOM", "GONZALEZ");
        person.put("ARRIVEE", "10:00");
        JSONObject blank = new JSONObject();
        java.util.List<JSONObject> rows = new java.util.ArrayList<JSONObject>();
        rows.add(person);
        rows.add(blank);
        StructuredRecognitionPipeline.dropBlankNameAndClocks(rows);
        assertEquals(1, rows.size());
        assertEquals("GONZALEZ", rows.get(0).getString("NOM_PRENOM"));
    }
}
