package com.attendance.recognition;

import com.attendance.recognition.dto.CellEvidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekCellAdvisorTest {

    @Test
    void applyWritesLegalTime() {
        CellEvidence ev = new CellEvidence();
        ev.hasInk = true;
        DeepSeekCellAdvisor.apply(ev, "{\"time\":\"19:00\",\"illegible\":false}");
        assertEquals("19:00", ev.text);
        assertEquals("harness", ev.source);
        assertFalse(ev.lowConfidence);
        assertEquals("19:00", ev.diag.get("flashTime"));
        assertEquals(Boolean.FALSE, ev.diag.get("flashIllegible"));
        assertEquals(Boolean.TRUE, ev.diag.get("flashCalled"));
    }

    @Test
    void illegibleClearsTime() {
        CellEvidence ev = new CellEvidence();
        ev.hasInk = true;
        DeepSeekCellAdvisor.apply(ev, "{\"time\":\"\",\"illegible\":true}");
        assertEquals("", ev.text);
        assertTrue(ev.lowConfidence);
    }

    @Test
    void cellPromptForbidsNeighborGuess() {
        assertTrue(DeepSeekCellAdvisor.CELL_PROMPT.contains("Do not guess from shift"));
        assertTrue(!DeepSeekCellAdvisor.CELL_PROMPT.contains("10:00"));
    }
}
