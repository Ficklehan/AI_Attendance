package com.attendance.recognition;

import com.attendance.recognition.dto.CellOcrResult;
import com.attendance.recognition.ocr.DshOcrLocalClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DshOcrLocalClientParseTest {

    @Test
    void parseFullTakesMinConfidenceAndJoinsLines() {
        String json = "{\"lines\":["
                + "{\"text\":\"19:00\",\"confidence\":0.91,\"low_confidence\":false},"
                + "{\"text\":\"extra\",\"confidence\":0.4,\"low_confidence\":true}"
                + "]}";
        CellOcrResult result = DshOcrLocalClient.parseFull(json);
        assertTrue(result.text.contains("19:00"));
        assertEquals(0.4d, result.confidence, 0.0001d);
        assertTrue(result.lowConfidence);
    }

    @Test
    void emptyLinesYieldEmptyResult() {
        CellOcrResult result = DshOcrLocalClient.parseFull("{\"lines\":[]}");
        assertEquals("", result.text);
        assertEquals(0, result.confidence, 0.0001d);
    }

    @Test
    void parseFullMarksServeErrorAsLowConfidence() {
        CellOcrResult result = DshOcrLocalClient.parseFull(
                "{\"id\":\"1\",\"error\":\"boom\",\"lines\":[]}");
        assertTrue(result.lowConfidence);
        assertEquals("", result.text);
    }
}
