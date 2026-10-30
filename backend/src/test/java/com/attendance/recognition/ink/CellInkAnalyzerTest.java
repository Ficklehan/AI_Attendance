package com.attendance.recognition.ink;

import com.attendance.config.RecognitionProperties;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellInkAnalyzerTest {

    @Test
    void printedColonIsNotClockInk() {
        BufferedImage image = white(240, 80);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(116, 28, 6, 6);
        g.fillRect(116, 46, 6, 6);
        g.dispose();
        RecognitionProperties.Rule rule = new RecognitionProperties().getRule();
        assertFalse(CellInkAnalyzer.analyzeClock(image, rule).hasInk);
    }

    @Test
    void printedFourBoxRulingIsNotClockInk() {
        BufferedImage image = white(240, 80);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, 239, 79);
        g.drawLine(60, 0, 60, 79);
        g.drawLine(120, 0, 120, 79);
        g.drawLine(180, 0, 180, 79);
        g.fillRect(116, 28, 6, 6);
        g.fillRect(116, 46, 6, 6);
        g.dispose();
        RecognitionProperties.Rule rule = new RecognitionProperties().getRule();
        assertFalse(CellInkAnalyzer.analyzeClock(image, rule).hasInk);
    }

    @Test
    void handwrittenDigitsOnLeftCountAsClockInk() {
        BufferedImage image = white(240, 80);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(18, 18, 46, 44);
        g.fillRect(116, 28, 6, 6);
        g.fillRect(116, 46, 6, 6);
        g.dispose();
        RecognitionProperties.Rule rule = new RecognitionProperties().getRule();
        assertTrue(CellInkAnalyzer.analyzeClock(image, rule).hasInk);
    }

    private static BufferedImage white(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }
}
