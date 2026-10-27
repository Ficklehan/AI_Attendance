package com.attendance.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionImageEnhanceSupportTest {

    @Test
    void emptyInputIsUnchanged() {
        RecognitionImageEnhanceSupport.Result empty =
                RecognitionImageEnhanceSupport.enhance(new byte[0], "image/jpeg");
        assertFalse(empty.isChanged());
        assertEquals(0, empty.getBytes().length);
    }

    @Test
    void garbageBytesStayOriginal() {
        byte[] garbage = new byte[]{1, 2, 3, 4, 5};
        RecognitionImageEnhanceSupport.Result result =
                RecognitionImageEnhanceSupport.enhance(garbage, "image/jpeg");
        assertFalse(result.isChanged());
        assertEquals(garbage, result.getBytes());
    }

    @Test
    void fadedSheetBecomesReadableJpeg() throws Exception {
        byte[] png = fadedSheetPng(640, 480);
        RecognitionImageEnhanceSupport.Result result =
                RecognitionImageEnhanceSupport.enhance(png, "image/png");
        assertTrue(result.isChanged());
        assertEquals("image/jpeg", result.getMimeType());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.getBytes()));
        assertNotNull(out);
        assertEquals(640, out.getWidth());
        assertEquals(480, out.getHeight());
        assertTrue(luminanceRange(out) > luminanceRange(ImageIO.read(new ByteArrayInputStream(png))));
    }

    @Test
    void imageUnderCapKeepsSize() throws Exception {
        BufferedImage src = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 320, 240);
        g.dispose();
        BufferedImage limited = RecognitionImageEnhanceSupport.limitLongEdge(src);
        assertEquals(320, limited.getWidth());
        assertEquals(240, limited.getHeight());
    }

    @Test
    void smallSkewOfRuledSheetIsDetected() throws Exception {
        BufferedImage sheet = ruledSheet(400, 500);
        BufferedImage tilted = RecognitionImageEnhanceSupport.rotate(sheet, 2.0d);
        double estimated = RecognitionImageEnhanceSupport.estimateDeskewDegrees(tilted);
        assertTrue(Math.abs(estimated - 2.0d) <= 1.0d, "deskew=" + estimated);
    }

    private static byte[] fadedSheetPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(188, 188, 188));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(150, 150, 150));
        for (int y = 40; y < height - 20; y += 28) {
            g.drawLine(24, y, width - 24, y);
        }
        g.setColor(new Color(120, 120, 120));
        g.drawString("15:41", 80, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return out.toByteArray();
    }

    private static BufferedImage ruledSheet(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.DARK_GRAY);
        for (int y = 40; y < height - 20; y += 22) {
            g.fillRect(16, y, width - 32, 2);
        }
        g.dispose();
        return image;
    }

    private static int luminanceRange(BufferedImage image) {
        int min = 255;
        int max = 0;
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 4) {
                int rgb = image.getRGB(x, y);
                int lum = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
                if (lum < min) {
                    min = lum;
                }
                if (lum > max) {
                    max = lum;
                }
            }
        }
        return max - min;
    }
}
