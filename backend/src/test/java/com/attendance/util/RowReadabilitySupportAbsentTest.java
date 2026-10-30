package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowReadabilitySupportAbsentTest {

    @Test
    void keepsTimesWhenNeighborAbsentLeaksIntoObservations() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "NSUMBU MAYALA MARIE PAUL");
        record.put("Observations", "ABSENT");
        record.put("SIGNATURE", "Thiam");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "14:00");
        record.put("PAUSE", "0");

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("09:00", record.getString("ARRIVEE"));
        assertEquals("14:00", record.getString("DEPAR"));
        assertEquals("0", record.getString("PAUSE"));
        assertEquals("正常", record.getString("Mark"));
        assertEquals("", record.getString("Observations"));
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
    }

    @Test
    void clearsCopiedTimesWhenAbsentAndTokenSigned() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "JOSEPH Lucien (artiste) (18/09/1988)");
        record.put("HORAIRES_DU_TRAVAIL", "08:00-14:00");
        record.put("Observations", "ABSENT");
        record.put("SIGNATURE", "已签字");
        record.put("SIGNATURE_RAW", "已签字");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "14:00");
        record.put("PAUSE", "0");

        assertTrue(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("", record.getString("DEPAR"));
        assertEquals("", record.getString("PAUSE"));
        assertEquals("未出勤", record.getString("Mark"));
        assertEquals("ABSENT", record.getString("Observations"));
        assertEquals("", record.getString("SIGNATURE"));
        assertTrue(RowReadabilitySupport.isAbsentRow(record));
    }

    @Test
    void keepsTimesAndClearsAbsentWhenPunchFilled() {
        JSONObject record = new JSONObject();
        record.put("Observations", "");
        record.put("Mark", "未出勤");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "14:00");
        record.put("PAUSE", "0");

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("09:00", record.getString("ARRIVEE"));
        assertEquals("14:00", record.getString("DEPAR"));
        // 有效到离优先于模型未出勤初判（人工更正后的事实）
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
    }

    @Test
    void clearsCopiedTimesWhenAbsentAndUnsigned() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "JOSEPH Lucien");
        record.put("Observations", "ABSENT");
        record.put("SIGNATURE", "");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "14:00");
        record.put("PAUSE", "0");

        assertTrue(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("", record.getString("DEPAR"));
        assertEquals("", record.getString("PAUSE"));
        assertEquals("未出勤", record.getString("Mark"));
        assertEquals("ABSENT", record.getString("Observations"));
        assertTrue(RowReadabilitySupport.isAbsentRow(record));
    }

    @Test
    void stillNormalizesAbsentRowWithoutPunchTimes() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "JOSEPH Lucien");
        record.put("Observations", "ABSENT");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        record.put("PAUSE", "00");

        assertTrue(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("", record.getString("DEPAR"));
        assertEquals("", record.getString("PAUSE"));
        assertEquals("未出勤", record.getString("Mark"));
        assertTrue(RowReadabilitySupport.isAbsentRow(record));
    }

    @Test
    void blurMarkWinsOverLeakedAbsentAndEmptyClocks() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "NSUMBU MAYALA MARIE PAUL");
        record.put("Observations", "ABSENT");
        record.put("Mark", "模糊");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        record.put("PAUSE", "");

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(record));
        RowReadabilitySupport.dropAbsentIfBlurred(record);
        assertEquals("模糊", record.getString("Mark"));
        assertEquals("", record.getString("Observations"));
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
        assertTrue(RowReadabilitySupport.hasBlurMark(record));
    }

    @Test
    void emptyClocksWithoutBlurStayAbsent() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "Jean Dupont");
        record.put("Observations", "");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertFalse(RowReadabilitySupport.hasBlurMark(record));
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
        record.put("Mark", "未出勤");
        record.put("SmartMark", "未出勤");
        assertTrue(RowReadabilitySupport.isAbsentRow(record));
        assertEquals("未出勤", record.getString("Mark"));
    }

    @Test
    void neverKeepsBlurAndAbsentTogether() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "Jean Dupont");
        record.put("Mark", "模糊;未出勤");
        record.put("SmartMark", "模糊;未出勤");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");

        RowReadabilitySupport.dropAbsentIfBlurred(record);
        assertEquals("模糊", record.getString("Mark"));
        assertEquals("模糊", record.getString("SmartMark"));
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
        assertTrue(RowReadabilitySupport.hasBlurMark(record));
    }

    @Test
    void leavesNormalRowUntouched() {
        JSONObject record = new JSONObject();
        record.put("Observations", "RAS");
        record.put("Mark", "正常");
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "18:00");
        record.put("PAUSE", "30");

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(record));
        assertEquals("09:00", record.getString("ARRIVEE"));
        assertEquals("正常", record.getString("Mark"));
    }

    @Test
    void detectsAbsentInMixedCaseObservations() {
        JSONObject record = new JSONObject();
        record.put("Observations", "Absent - maladie");
        assertTrue(RowReadabilitySupport.observationsIndicateAbsent(record));
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
        record.put("Mark", "未出勤");
        assertTrue(RowReadabilitySupport.isAbsentRow(record));
    }

    @Test
    void blurMarkWithEmptyClocksIsNotAbsent() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "Jean Dupont");
        record.put("Mark", "模糊");
        record.put("SmartMark", "模糊");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertFalse(RowReadabilitySupport.isAbsentRow(record));
        assertTrue(RowReadabilitySupport.hasBlurMark(record));
    }

    @Test
    void dropsPrintedBlankRowWithoutNameOrTimes() {
        JSONObject record = new JSONObject();
        record.put("NO", "5");
        record.put("NOM_PRENOM", "-");
        record.put("Entrepot", "TOULOUSE");
        record.put("AGENCE_INTERIMAIRE", "STAFFMATCH");
        record.put("HORAIRES_DU_TRAVAIL", "10:00-13:00");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertTrue(RowReadabilitySupport.isNamelessEmptyClockRow(record));
    }

    @Test
    void keepsNamedAbsentRow() {
        JSONObject record = new JSONObject();
        record.put("NO", "5");
        record.put("NOM_PRENOM", "Jean Dupont");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        record.put("SmartMark", "未出勤");
        assertFalse(RowReadabilitySupport.isNamelessEmptyClockRow(record));
    }

    @Test
    void keepsQuestionMarkNameWithEmptyClocksAsBlurNotBlank() {
        JSONObject record = new JSONObject();
        record.put("NO", "1");
        record.put("NOM_PRENOM", "???");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertFalse(RowReadabilitySupport.isNamelessEmptyClockRow(record));
    }

    @Test
    void keepsSanitizedUnreadableNameWithEmptyClocks() {
        JSONObject record = new JSONObject();
        record.put("NO", "1");
        record.put("NOM_PRENOM", "");
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        com.alibaba.fastjson.JSONArray unreadable = new com.alibaba.fastjson.JSONArray();
        unreadable.add("NOM_PRENOM");
        record.put(RecognizedFieldSanitizer.UNREADABLE_FIELDS_KEY, unreadable);
        assertFalse(RowReadabilitySupport.isNamelessEmptyClockRow(record));
    }

    @Test
    void wipesShiftCopyWhenUnsigned() {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", "JOSEPH Lucien");
        record.put("HORAIRES_DU_TRAVAIL", "08:00-14:00");
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "14:00");
        record.put("PAUSE", "0");
        record.put("SIGNATURE", "");
        record.put("Observations", "");
        record.put("Mark", "正常");

        List<JSONObject> rows = Collections.singletonList(record);
        assertEquals(1, RowReadabilitySupport.applyCopiedClockWipe(rows));
        assertEquals("", record.getString("ARRIVEE"));
        assertEquals("", record.getString("DEPAR"));
        assertEquals("未出勤", record.getString("Mark"));
    }

    @Test
    void wipesNeighborIdenticalClocksWhenAbsent() {
        JSONObject row1 = frenchRow("JOSEPH Lucien", "09:00", "14:00", "已签字", "ABSENT");
        JSONObject row2 = frenchRow("THIAM BASSIROU", "09:00", "14:00", "Thiam", "");
        JSONObject row3 = frenchRow("NSUMBU MAYALA MARIE PAUL", "09:00", "14:00", "Nsumbu", "");
        List<JSONObject> rows = Arrays.asList(row1, row2, row3);

        assertTrue(RowReadabilitySupport.applyAbsentObservationRule(row1));
        assertEquals(0, RowReadabilitySupport.applyCopiedClockWipe(rows));
        assertEquals("", row1.getString("ARRIVEE"));
        assertEquals("", row1.getString("DEPAR"));
        assertEquals("未出勤", row1.getString("Mark"));
        assertEquals("09:00", row2.getString("ARRIVEE"));
        assertEquals("14:00", row2.getString("DEPAR"));
        assertEquals("09:00", row3.getString("ARRIVEE"));
        assertEquals("14:00", row3.getString("DEPAR"));
    }

    @Test
    void wipesNeighborCopyWhenSignatureEmpty() {
        JSONObject emptySig = frenchRow("JOSEPH Lucien", "09:00", "14:00", "", "");
        JSONObject present = frenchRow("THIAM BASSIROU", "09:00", "14:00", "Thiam", "");
        List<JSONObject> rows = Arrays.asList(emptySig, present);

        assertEquals(1, RowReadabilitySupport.applyCopiedClockWipe(rows));
        assertEquals("", emptySig.getString("ARRIVEE"));
        assertEquals("", emptySig.getString("DEPAR"));
        assertEquals("未出勤", emptySig.getString("Mark"));
        assertEquals("09:00", present.getString("ARRIVEE"));
        assertEquals("14:00", present.getString("DEPAR"));
    }

    @Test
    void keepsRealSignedPresentDifferentFromNeighbor() {
        JSONObject present = frenchRow("THIAM BASSIROU", "09:00", "14:00", "Thiam", "");
        JSONObject neighbor = frenchRow("NSUMBU MAYALA MARIE PAUL", "09:05", "14:10", "Nsumbu", "");
        List<JSONObject> rows = Arrays.asList(present, neighbor);

        assertFalse(RowReadabilitySupport.applyAbsentObservationRule(present));
        assertEquals(0, RowReadabilitySupport.applyCopiedClockWipe(rows));
        assertEquals("09:00", present.getString("ARRIVEE"));
        assertEquals("14:00", present.getString("DEPAR"));
        assertEquals("正常", present.getString("Mark"));
    }

    @Test
    void keepsTwoPresentRowsWithSameHandwrittenTimes() {
        JSONObject row2 = frenchRow("THIAM BASSIROU", "09:00", "14:00", "已签字", "");
        JSONObject row3 = frenchRow("NSUMBU MAYALA MARIE PAUL", "09:00", "14:00", "已签字", "");
        List<JSONObject> rows = Arrays.asList(row2, row3);

        assertEquals(0, RowReadabilitySupport.applyCopiedClockWipe(rows));
        assertEquals("09:00", row2.getString("ARRIVEE"));
        assertEquals("09:00", row3.getString("ARRIVEE"));
        assertEquals("正常", row2.getString("Mark"));
    }

    private static JSONObject frenchRow(String name, String arrive, String depart,
                                        String signature, String observations) {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", name);
        record.put("Date", "2026-09-21");
        record.put("Entrepot", "MARLY");
        record.put("HORAIRES_DU_TRAVAIL", "08:00-14:00");
        record.put("ARRIVEE", arrive);
        record.put("DEPAR", depart);
        record.put("PAUSE", "0");
        record.put("SIGNATURE", signature);
        record.put("Observations", observations);
        record.put("Mark", "正常");
        return record;
    }
}
