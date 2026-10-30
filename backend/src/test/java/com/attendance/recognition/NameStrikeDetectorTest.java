package com.attendance.recognition;

import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.ink.CellInkAnalyzer;
import com.attendance.recognition.ink.NameStrikeDetector;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameStrikeDetectorTest {

    @Test
    void horizontalStrikeThroughName() {
        BufferedImage img = white(200, 40);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(5, 18, 190, 3);
        g.dispose();
        NameStrikeDetector.StrikeResult r = NameStrikeDetector.detect(img, new RecognitionProperties.Rule());
        assertTrue(r.struck);
        assertFalse(r.hasReplacement);
    }

    @Test
    void blankCellHasNoInk() {
        BufferedImage img = white(80, 40);
        assertFalse(CellInkAnalyzer.analyze(img, new RecognitionProperties.Rule()).hasInk);
    }

    @Test
    void scribbleCountsAsInk() {
        BufferedImage img = white(80, 40);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(10, 8, 50, 22);
        g.dispose();
        assertTrue(CellInkAnalyzer.analyze(img, new RecognitionProperties.Rule()).hasInk);
    }

    private static BufferedImage white(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
