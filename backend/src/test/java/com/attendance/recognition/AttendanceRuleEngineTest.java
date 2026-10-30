package com.attendance.recognition;

import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.recognition.dto.OcrRawResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceRuleEngineTest {

    private AttendanceRuleEngine engine;

    @BeforeEach
    void setUp() {
        RecognitionProperties props = new RecognitionProperties();
        engine = new AttendanceRuleEngine(props);
    }

    @Test
    void longLineWithLegalTimesIsNormalNotAbsent() {
        OcrRawResult raw = presentRow("10:00", "19:00");
        raw.depart.hasLongLine = true;
        raw.depart.inkAfterLineRemoval = true;
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertEquals("10:00", rec.getString("ARRIVEE"));
        assertEquals("19:00", rec.getString("DEPAR"));
        assertTrue(rec.getString("Mark").contains("正常"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
        assertFalse(rec.getBooleanValue("isDeleted"));
    }

    @Test
    void bothCellsNoInkIsAbsentNotDeleted() {
        OcrRawResult raw = new OcrRawResult();
        raw.rowIndex = 8;
        raw.name = "KHONDOKER";
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertEquals("", rec.getString("ARRIVEE"));
        assertEquals("", rec.getString("DEPAR"));
        assertEquals("未出勤", rec.getString("Mark"));
        assertEquals("no_ink_both", rec.getString("_markWhy"));
        assertFalse(rec.getBooleanValue("isDeleted"));
    }

    @Test
    void inkWithoutLegalTimeIsBlurNotAbsent() {
        OcrRawResult raw = new OcrRawResult();
        raw.name = "MESSY";
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        raw.arrive.text = "";
        raw.depart.hasInk = true;
        raw.depart.inkAfterLineRemoval = true;
        raw.depart.text = "";
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertEquals("模糊", rec.getString("Mark"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
        assertEquals("", rec.getString("ARRIVEE"));
        assertTrue(rec.getString("_markWhy").startsWith("blur:"));
        assertTrue(rec.getString("_markWhy").contains("ink_no_legal"));
    }

    @Test
    void nameStruckAndNoClockInkIsDeletedAbsent() {
        OcrRawResult raw = new OcrRawResult();
        raw.name = "UDDIN";
        raw.nameStruck = true;
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertEquals("未出勤", rec.getString("Mark"));
        assertTrue(rec.getBooleanValue("isDeleted"));
        assertEquals("", rec.getString("ARRIVEE"));
        assertEquals("", rec.getString("DEPAR"));
    }

    @Test
    void nameStruckWithReplacementIsNotDeleted() {
        OcrRawResult raw = presentRow("08:00", "17:00");
        raw.nameStruck = true;
        raw.nameHasReplacement = true;
        raw.name = "NEW NAME";
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertFalse(rec.getBooleanValue("isDeleted"));
        assertEquals("NEW NAME", rec.getString("NOM_PRENOM"));
        assertEquals("08:00", rec.getString("ARRIVEE"));
    }

    @Test
    void ocrBareZeroIsBlurNotLegalClock() {
        OcrRawResult raw = new OcrRawResult();
        raw.name = "GAYE";
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        raw.arrive.source = "ocr";
        raw.arrive.text = "0";
        raw.arrive.confidence = 0.94d;
        raw.depart.hasInk = true;
        raw.depart.inkAfterLineRemoval = true;
        raw.depart.source = "ocr";
        raw.depart.text = "0010";
        raw.depart.confidence = 0.88d;
        JSONObject rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertEquals("", rec.getString("ARRIVEE"));
        assertEquals("", rec.getString("DEPAR"));
        assertTrue(rec.getString("Mark").contains("模糊"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
    }

    @Test
    void emptyTimesWithInkMustNotBecomeAbsent() {
        OcrRawResult raw = new OcrRawResult();
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        JSONObject rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertFalse(rec.getString("Mark").contains("未出勤"));
        assertTrue(rec.getString("Mark").contains("模糊"));
    }

    @Test
    void oneSidedLegalTimeIsMissingPunch() {
        OcrRawResult raw = new OcrRawResult();
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        raw.arrive.text = "09:00";
        raw.arrive.confidence = 0.9d;
        raw.depart.hasInk = true;
        raw.depart.inkAfterLineRemoval = true;
        raw.depart.text = "18:00";
        raw.depart.confidence = 0.9d;
        raw.depart.text = "";
        raw.depart.hasInk = false;
        raw.depart.inkAfterLineRemoval = false;
        JSONObject rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertTrue(rec.getString("Mark").contains("缺卡"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
    }

    @Test
    void missingArriveBoxIsBlurNotAbsent() {
        OcrRawResult raw = new OcrRawResult();
        raw.name = "ROW";
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        raw.arrive.lowConfidence = true;
        raw.arrive.source = "ground_missing";
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertTrue(rec.getString("Mark").contains("模糊"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
    }

    @Test
    void midConfidenceKeepsCandidateAndMarksBlur() {
        OcrRawResult raw = presentRow("10:00", "19:00");
        raw.depart.confidence = 0.6d;
        raw.depart.lowConfidence = false;
        JSONObject rec = engine.apply(raw, "IT", NightShiftConfigDTO.defaults());
        assertEquals("19:00", rec.getString("DEPAR"));
        assertTrue(rec.getString("Mark").contains("模糊"));
    }

    @Test
    void pauseKeepsSingleNumberAndDropsStackedGarbage() {
        OcrRawResult raw = presentRow("10:00", "19:00");
        raw.pauseText = "60 60";
        JSONObject rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertEquals("60", rec.getString("PAUSE"));

        raw.pauseText = "6060 min";
        rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertEquals("", rec.getString("PAUSE"));

        raw.pauseText = "690 min";
        rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertEquals("", rec.getString("PAUSE"));
    }

    @Test
    void incompleteFlashTimeIsPaddedAndLegalSideKeptOnBlurRow() {
        OcrRawResult raw = presentRow("", "19:0");
        raw.arrive.text = "";
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        JSONObject rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertEquals("", rec.getString("ARRIVEE"));
        assertEquals("19:00", rec.getString("DEPAR"));
        assertTrue(rec.getString("Mark").contains("模糊"));
        assertFalse(rec.getString("Mark").contains("未出勤"));
    }

    @Test
    void nameDropsQuestionMarks() {
        OcrRawResult raw = presentRow("10:00", "19:00");
        raw.name = "MADY ? TRAORE";
        JSONObject rec = engine.apply(raw, "FR", NightShiftConfigDTO.defaults());
        assertEquals("MADY TRAORE", rec.getString("NOM_PRENOM"));
    }

    private static OcrRawResult presentRow(String arrive, String depart) {
        OcrRawResult raw = new OcrRawResult();
        raw.name = "SISSOKO";
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        raw.arrive.text = arrive;
        raw.arrive.confidence = 0.92d;
        raw.depart.hasInk = true;
        raw.depart.inkAfterLineRemoval = true;
        raw.depart.text = depart;
        raw.depart.confidence = 0.92d;
        return raw;
    }
}
