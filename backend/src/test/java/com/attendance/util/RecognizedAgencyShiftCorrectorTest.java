package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognizedAgencyShiftCorrectorTest {

    @Test
    void moves_agency_from_horaires_when_agence_empty() {
        JSONObject record = new JSONObject();
        record.put("AGENCE_INTERIMAIRE", "");
        record.put("HORAIRES_DU_TRAVAIL", "STARTPEOPLE");

        RecognizedAgencyShiftCorrector.correctSwappedFields(record);

        assertEquals("STARTPEOPLE", record.getString("AGENCE_INTERIMAIRE"));
        assertEquals("", record.getString("HORAIRES_DU_TRAVAIL"));
    }

    @Test
    void swaps_when_both_columns_reversed() {
        JSONObject record = new JSONObject();
        record.put("AGENCE_INTERIMAIRE", "14:30-21:00");
        record.put("HORAIRES_DU_TRAVAIL", "MANPOWER");

        RecognizedAgencyShiftCorrector.correctSwappedFields(record);

        assertEquals("MANPOWER", record.getString("AGENCE_INTERIMAIRE"));
        assertEquals("14:30-21:00", record.getString("HORAIRES_DU_TRAVAIL"));
    }

    @Test
    void leaves_correct_rows_unchanged() {
        JSONObject record = new JSONObject();
        record.put("AGENCE_INTERIMAIRE", "JOB&TALENT");
        record.put("HORAIRES_DU_TRAVAIL", "22:00-06:00");

        RecognizedAgencyShiftCorrector.correctSwappedFields(record);

        assertEquals("JOB&TALENT", record.getString("AGENCE_INTERIMAIRE"));
        assertEquals("22:00-06:00", record.getString("HORAIRES_DU_TRAVAIL"));
    }

    @Test
    void detects_known_agency_tokens() {
        assertTrue(RecognizedAgencyShiftCorrector.looksLikeAgencyName("STAFFMATCH"));
        assertTrue(RecognizedAgencyShiftCorrector.looksLikeAgencyName("JOB&TALENT"));
        assertFalse(RecognizedAgencyShiftCorrector.looksLikeAgencyName("14:30-21:00"));
        assertFalse(RecognizedAgencyShiftCorrector.looksLikeAgencyName("MATIN"));
    }

    @Test
    void detects_shift_labels() {
        assertTrue(RecognizedAgencyShiftCorrector.looksLikeShiftValue("MATIN"));
        assertTrue(RecognizedAgencyShiftCorrector.looksLikeShiftValue("22:00-06:00"));
        assertFalse(RecognizedAgencyShiftCorrector.looksLikeShiftValue("STARTPEOPLE"));
    }
}
