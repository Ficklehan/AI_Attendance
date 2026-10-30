package com.attendance.recognition;

import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.harness.CropZoomTool;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropZoomToolTest {

    @Test
    void nearestScaleCapsAtMaxAndHitsTarget() {
        BufferedImage src = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLACK);
        g.fillRect(10, 10, 20, 10);
        g.dispose();

        BufferedImage out = CropZoomTool.cropZoom(src, new BBox(10, 10, 20, 10), 1600, 16, "nearest");
        int longEdge = Math.max(out.getWidth(), out.getHeight());
        assertTrue(longEdge >= 160 && longEdge <= 1600);
        assertEquals(20 * 16, out.getWidth());
        assertEquals(10 * 16, out.getHeight());
    }

    @Test
    void previewRemapScalesBoxToSource() {
        BBox preview = new BBox(100, 50, 40, 20);
        BBox src = preview.remapFromPreview(1000, 2000);
        assertEquals(200, src.x);
        assertEquals(100, src.y);
        assertEquals(80, src.w);
        assertEquals(40, src.h);
    }
}
