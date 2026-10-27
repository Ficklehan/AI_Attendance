package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockCellInkDetectorTest {

    @Test
    void blankPrintedColonBandIsNotInk() {
        BufferedImage image = whiteSheet(400, 300);
        assertFalse(ClockCellInkDetector.hasClockInk(image, 0, 2));
    }

    @Test
    void darkScribbleInClockBandCountsAsInk() {
        BufferedImage image = whiteSheet(400, 300);
        scribbleClockCell(image, 1, 2);
        assertTrue(ClockCellInkDetector.hasClockInk(image, 1, 2));
    }

    @Test
    void absentRowWithInkBecomesBlurReview() {
        BufferedImage image = whiteSheet(400, 300);
        scribbleClockCell(image, 1, 2);
        JSONObject absentBlank = absentRow("JOSEPH");
        JSONObject absentInk = absentRow("THIAM");
        List<JSONObject> rows = Arrays.asList(absentBlank, absentInk);

        int changed = ClockCellInkDetector.overrideAbsentWhenClockInkPresent(image, rows);

        assertEquals(1, changed);
        assertEquals("未出勤", absentBlank.getString("Mark"));
        assertEquals("模糊", absentInk.getString("Mark"));
        assertEquals("模糊", absentInk.getString("SmartMark"));
        assertEquals("clock_ink", absentInk.getString("_reviewReason"));
        assertEquals("", absentInk.getString("ExceptionType"));
    }

    @Test
    void filledTimesAreNotInspected() {
        BufferedImage image = whiteSheet(400, 300);
        scribbleClockCell(image, 0, 1);
        JSONObject present = absentRow("THIAM");
        present.put("ARRIVEE", "09:00");
        present.put("DEPAR", "14:00");
        present.put("Mark", "正常");
        present.put("SmartMark", "正常");
        assertEquals(0, ClockCellInkDetector.overrideAbsentWhenClockInkPresent(image, Arrays.asList(present)));
        assertEquals("正常", present.getString("Mark"));
    }

    private static JSONObject absentRow(String name) {
        JSONObject record = new JSONObject();
        record.put("NOM_PRENOM", name);
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        record.put("Mark", "未出勤");
        record.put("SmartMark", "未出勤");
        return record;
    }

    private static BufferedImage whiteSheet(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static void scribbleClockCell(BufferedImage image, int rowIndex, int rowCount) {
        int w = image.getWidth();
        int h = image.getHeight();
        int tableTop = (int) Math.floor(h * 0.30d);
        int tableBottom = (int) Math.floor(h * 0.98d);
        int x0 = (int) Math.floor(w * 0.48d);
        int x1 = (int) Math.floor(w * 0.78d);
        int slots = rowCount + 1;
        int band = (tableBottom - tableTop) / slots;
        int y0 = tableTop + band * (rowIndex + 1);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        for (int i = 0; i < 12; i++) {
            g.fillOval(x0 + 8 + i * 6, y0 + 4 + (i % 3) * 5, 10, 8);
        }
        g.drawLine(x0 + 6, y0 + 6, x1 - 8, y0 + band / 2);
        g.dispose();
    }
}
