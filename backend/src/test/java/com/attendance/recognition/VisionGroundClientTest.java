package com.attendance.recognition;

import com.attendance.recognition.dto.PageLayout;
import com.attendance.recognition.harness.VisionGroundClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionGroundClientTest {

    @Test
    void parseLayoutReadsCellsAndHeader() {
        String raw = "```json\n{\"warehouse\":\"roma\",\"dateRaw\":\"28/10/2026\",\"pageNum\":\"1\","
                + "\"cells\":[{\"row\":2,\"field\":\"DEPAR\",\"x\":10,\"y\":20,\"w\":30,\"h\":12}]}\n```";
        PageLayout layout = VisionGroundClient.parseLayout(raw, 1000);
        assertEquals("roma", layout.warehouse);
        assertEquals("28/10/2026", layout.dateRaw);
        assertEquals(1, layout.cells.size());
        assertEquals("DEPAR", layout.cells.get(0).field);
        assertEquals(2, layout.cells.get(0).row);
        assertEquals(30, layout.cells.get(0).bbox.w);
    }

    @Test
    void parseLayoutAcceptsXyxyAndHeaderAliases() {
        String raw = "{\"cells\":[{\"row\":1,\"field\":\"Arrivo\",\"x1\":10,\"y1\":20,\"x2\":40,\"y2\":32},"
                + "{\"row\":1,\"field\":\"Uscita\",\"bbox\":{\"x\":50,\"y\":20,\"width\":30,\"height\":12}}]}";
        PageLayout layout = VisionGroundClient.parseLayout(raw, 1000);
        assertEquals(2, layout.cells.size());
        assertEquals("ARRIVEE", layout.cells.get(0).field);
        assertEquals(10, layout.cells.get(0).bbox.x);
        assertEquals(12, layout.cells.get(0).bbox.h);
        assertEquals("DEPAR", layout.cells.get(1).field);
        assertEquals(30, layout.cells.get(1).bbox.w);
    }

    @Test
    void headerPromptHasNoCellsAndNoExampleTimes() {
        String prompt = VisionGroundClient.buildHeaderPrompt();
        assertTrue(prompt.contains("warehouse"));
        assertTrue(!prompt.contains("ARRIVEE"));
        assertTrue(!prompt.contains("19:00"));
    }

    @Test
    void promptContainsItalianUscitaAndNoExampleTimes() {
        String prompt = VisionGroundClient.buildPrompt("IT");
        assertTrue(prompt.contains("Uscita"));
        assertTrue(prompt.contains("Arrivo"));
        assertTrue(!prompt.contains("19:00"));
        assertTrue(!prompt.contains("SISSOKO"));
        assertTrue(prompt.contains("first data row"));
        assertTrue(prompt.contains("Do not skip rows"));
    }
}
