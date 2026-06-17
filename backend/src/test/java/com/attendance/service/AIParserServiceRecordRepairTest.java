package com.attendance.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AIParserServiceRecordRepairTest {

    @Test
    void repairInsertsClosingBracketBeforeNextRow() {
        String broken = "[\"41\",\"FRANCE\",\"MARLY\",\"2026-06-15\",\"SIDIBE Kamby\",\"STARTPEOPLE\",\"14H30 - 21H\",\"\",\"\",\"\",\"\",\"\",\n"
                + "[\"42\",\"FRANCE\",\"MARLY\",\"2026-06-15\",\"SIDIBE Mahamadou\",\"STARTPEOPLE\",\"14H30 - 21H\",\"\",\"\",\"\",\"\",\"\",\n"
                + "[\"43\",\"FRANCE\",\"MARLY\",\"2026-06-15\",\"SIDIBE Mody\",\"STARTPEOPLE\",\"14H30 - 21H\",\"14:30\",\"20:30\",\"30\",\"\",\"\"";
        String repaired = AIParserService.repairMissingRowClosingBrackets(broken);
        assertTrue(repaired.contains("\"41\""));
        assertTrue(repaired.contains("]\n[\"42\""));
        assertTrue(repaired.endsWith("]"));
    }

    @Test
    void repairDoesNotBreakValidRows() {
        String valid = "[\"1\",\"FR\"]\n[\"2\",\"DE\"]";
        String repaired = AIParserService.repairMissingRowClosingBrackets(valid);
        assertTrue(repaired.contains("]\n["));
    }
}
