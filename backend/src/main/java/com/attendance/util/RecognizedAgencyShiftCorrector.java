package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 纠正模型将供应商列与班次列互换的常见误识别。
 */
public final class RecognizedAgencyShiftCorrector {

    private static final Set<String> KNOWN_AGENCY_TOKENS = new HashSet<>(Arrays.asList(
            "MANPOWER", "STARTPEOPLE", "JOB&TALENT", "JOB AND TALENT", "STAFFMATCH",
            "ADECCO", "RANDSTAD", "ADEQUAT", "SYNERGIE", "PROMAN", "TEMPORIS",
            "AGENCE", "INTERIM", "INTERIMAIRE", "TEMPORARY", "RECRUIT"
    ));

    private static final List<String> SHIFT_LABELS = Arrays.asList(
            "matin", "soir", "nuit", "matin/soir", "matin / soir", "jour", "nuitée"
    );

    private RecognizedAgencyShiftCorrector() {
    }

    public static void correctSwappedFields(JSONObject record) {
        if (record == null) {
            return;
        }
        String agence = safe(record.getString("AGENCE_INTERIMAIRE"));
        String horaires = safe(record.getString("HORAIRES_DU_TRAVAIL"));

        boolean horairesLooksAgency = looksLikeAgencyName(horaires);
        boolean agenceLooksShift = looksLikeShiftValue(agence);
        boolean agenceLooksAgency = looksLikeAgencyName(agence);
        boolean agenceEmpty = agence.isEmpty();
        boolean horairesEmpty = horaires.isEmpty();

        if (!horairesLooksAgency) {
            return;
        }
        if (agenceLooksShift) {
            record.put("AGENCE_INTERIMAIRE", horaires);
            record.put("HORAIRES_DU_TRAVAIL", agence);
            return;
        }
        if (agenceEmpty || !agenceLooksAgency) {
            record.put("AGENCE_INTERIMAIRE", horaires);
            record.put("HORAIRES_DU_TRAVAIL", "");
        }
    }

    public static boolean looksLikeAgencyName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        if (looksLikeShiftValue(value)) {
            return false;
        }
        if (!RecognizedTimeNormalizer.extractTimeTokenStrings(value).isEmpty()) {
            return false;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        for (String token : KNOWN_AGENCY_TOKENS) {
            if (upper.contains(token)) {
                return true;
            }
        }
        if (upper.contains("PEOPLE") || upper.contains("TALENT") || upper.contains("MATCH")) {
            return true;
        }
        return upper.contains("&") && !upper.contains(":");
    }

    public static boolean looksLikeShiftValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        if (!RecognizedTimeNormalizer.extractTimeTokenStrings(value).isEmpty()) {
            return true;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        for (String label : SHIFT_LABELS) {
            if (lower.equals(label) || lower.contains(label)) {
                return true;
            }
        }
        return lower.matches(".*\\d{1,2}\\s*h(\\d{0,2})?.*");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
