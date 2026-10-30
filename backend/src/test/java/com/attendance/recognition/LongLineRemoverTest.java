package com.attendance.recognition;

import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.ink.LongLineRemover;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongLineRemoverTest {

    @Test
    void whiteCellHasNoLongLine() {
        BufferedImage src = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 80, 40);
        g.dispose();
        LongLineRemover.LineResult result = LongLineRemover.remove(src, new RecognitionProperties().getRule());
        assertFalse(result.hasLongLine);
        assertNotNull(result.cleaned);
    }

    @Test
    void diagonalStrokeIsDetectedAndCleaned() {
        BufferedImage src = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 80, 40);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawLine(4, 4, 76, 36);
        g.dispose();
        LongLineRemover.LineResult result = LongLineRemover.remove(src, new RecognitionProperties().getRule());
        assertTrue(result.hasLongLine);
        assertNotNull(result.cleaned);
    }
}
