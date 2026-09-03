package com.attendance.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIParserServiceRecordRepairTest {

    @Test
    void repairInsertsClosingBracketBeforeNextRow() {
        String broken = "[\"41\",\"FRANCE\",\"MARLY\",\"2026-06-15\",\"SIDIBE Kamby\",\"STARTPEOPLE\",\"14H30 - 21H\",\"\",\"\",\"\",\"\",\"\",\n"
                + "[\"42\",\"FRANCE\",\"MARLY\",\"2026-06-15\",\"SIDIBE Mahamadou\",\"STARTPEOPLE\",\"14H30 - 21H\",\"\",\"\",\"\",\"\",\"\",\n"
                + "[\"43\",\"FRANCE\",\"MARLY\",\"2026-06-15\",\"SIDIBE Mody\",\"STARTPEOPLE\",\"14H30 - 21H\",\"14:30\",\"20:30\",\"30\",\"\",\"\"";
        String repaired = AIParserService.repairMissingRowClosingBrackets(broken);
        assertTrue(repaired.contains("\"41\""));
        assertTrue(repaired.contains("[\"42\""), repaired);
        assertTrue(repaired.contains("],\n[\"42\"") || repaired.contains("]\n[\"42\""), repaired);
        assertTrue(repaired.endsWith("]"));
    }

    @Test
    void repairDoesNotBreakValidRows() {
        String valid = "[\"1\",\"FR\"]\n[\"2\",\"DE\"]";
        String repaired = AIParserService.repairMissingRowClosingBrackets(valid);
        assertTrue(repaired.contains("]\n["));
        assertEquals(valid, repaired);
    }

    @Test
    void repairDoesNotBreakCommaSeparatedNestedRows() {
        String valid = "[\n"
                + "[\"1\",\"France\",\"TOULOUSE\",\"2026-07-02\",\"Claid Bounga\",\"STAFFMATCH\",\"09:00-14:00\",\"09:00\",\"14:00\",\"0\",\"\",\"\",\"正常\",\"false\",\"1\"],\n"
                + "[\"2\",\"France\",\"TOULOUSE\",\"2026-07-02\",\"Khalil Saadi\",\"STAFFMATCH\",\"09:00-14:00\",\"09:00\",\"14:00\",\"30\",\"\",\"\",\"正常\",\"false\",\"1\"]\n"
                + "]";
        String repaired = AIParserService.repairMissingRowClosingBrackets(valid);
        assertFalse(repaired.startsWith("[]"), "must not insert ] after outer [");
        assertFalse(repaired.contains("],]"), "must not insert extra ] after valid row");
        assertTrue(repaired.contains("],\n[\"2\""));
    }
}
