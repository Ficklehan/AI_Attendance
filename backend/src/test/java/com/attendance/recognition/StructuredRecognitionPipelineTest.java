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
    void adviseDecisionSkipsLegalOcrAndNoInk() {
        com.attendance.recognition.dto.CellEvidence legal = new com.attendance.recognition.dto.CellEvidence();
        legal.hasInk = true;
        legal.inkAfterLineRemoval = true;
        legal.text = "10:00";
        legal.confidence = 0.94d;
        assertEquals("skip_ocr_legal", StructuredRecognitionPipeline.adviseDecision(legal, 0.50d));

        com.attendance.recognition.dto.CellEvidence blank = new com.attendance.recognition.dto.CellEvidence();
        assertEquals("skip_no_ink", StructuredRecognitionPipeline.adviseDecision(blank, 0.50d));

        com.attendance.recognition.dto.CellEvidence garbage = new com.attendance.recognition.dto.CellEvidence();
        garbage.hasInk = true;
        garbage.inkAfterLineRemoval = true;
        garbage.text = "";
        assertEquals("need_no_legal", StructuredRecognitionPipeline.adviseDecision(garbage, 0.50d));
    }

    @Test
    void harnessDefaultsPreferCliZoom800AndFlashPool8() {
        RecognitionProperties.Harness harness = new RecognitionProperties().getHarness();
        assertTrue(harness.isPreferCli());
        assertEquals(800, harness.getZoomTarget());
        assertEquals(8, harness.getVlmParallelism());
        assertEquals(24, harness.getMaxVlmCellsPerPage());
    }
}
