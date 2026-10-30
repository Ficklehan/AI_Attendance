package com.attendance.recognition;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockDigitGridTest {

    @Test
    void parseDigitAcceptsSingleGlyphOnly() {
        assertEquals(Character.valueOf('1'), ClockDigitGrid.parseDigit("1"));
        assertEquals(Character.valueOf('0'), ClockDigitGrid.parseDigit("O"));
        assertEquals(Character.valueOf('8'), ClockDigitGrid.parseDigit("B"));
        assertNull(ClockDigitGrid.parseDigit("0010"));
        assertNull(ClockDigitGrid.parseDigit("280:0"));
        assertNull(ClockDigitGrid.parseDigit("10"));
        assertNull(ClockDigitGrid.parseDigit(""));
    }

    @Test
    void composeRequiresFourDigits() {
        assertEquals("12:00", ClockDigitGrid.compose(new Character[] {
                Character.valueOf('1'), Character.valueOf('2'),
                Character.valueOf('0'), Character.valueOf('0')}));
        assertEquals("04:00", ClockDigitGrid.compose(new Character[] {
                Character.valueOf('0'), Character.valueOf('4'),
                Character.valueOf('0'), Character.valueOf('0')}));
        assertEquals("", ClockDigitGrid.compose(new Character[] {
                Character.valueOf('1'), Character.valueOf('0'), null, null}));
        assertEquals("", ClockDigitGrid.compose(new Character[] {
                Character.valueOf('2'), Character.valueOf('8'),
                Character.valueOf('0'), Character.valueOf('0')}));
    }

    @Test
    void locateUsesThreeRulingDividers() {
        BufferedImage image = new BufferedImage(200, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 40);
        g.setColor(Color.BLACK);
        g.fillRect(50, 0, 2, 40);
        g.fillRect(100, 0, 2, 40);
        g.fillRect(150, 0, 2, 40);
        g.dispose();
        ClockDigitGrid.Layout layout = ClockDigitGrid.locate(image, 165);
        assertEquals("ruling", layout.source);
        assertEquals(4, layout.boxes.length);
        for (int i = 0; i < 4; i++) {
            assertTrue(layout.boxes[i][2] > 8);
            assertTrue(layout.boxes[i][3] > 8);
            if (i > 0) {
                assertTrue(layout.boxes[i][0] >= layout.boxes[i - 1][0]);
            }
        }
    }

    @Test
    void templateSplitsColonGap() {
        BufferedImage image = new BufferedImage(200, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 40);
        g.dispose();
        ClockDigitGrid.Layout layout = ClockDigitGrid.locate(image, 165);
        assertEquals("template", layout.source);
        assertEquals(4, layout.boxes.length);
        int colonGap = layout.boxes[2][0] - (layout.boxes[1][0] + layout.boxes[1][2]);
        assertTrue(colonGap >= 8);
    }
}
