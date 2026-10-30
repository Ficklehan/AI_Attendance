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
    void fadedSheetBecomesReadableJpegNearBudget() throws Exception {
        byte[] png = fadedSheetPng(640, 480);
        RecognitionImageEnhanceSupport.Result result =
                RecognitionImageEnhanceSupport.enhance(png, "image/png");
        assertTrue(result.isChanged());
        assertEquals("image/jpeg", result.getMimeType());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.getBytes()));
        assertNotNull(out);
        long pixels = (long) out.getWidth() * (long) out.getHeight();
        double ratio = pixels / (double) RecognitionImageEnhanceSupport.MODEL_PIXEL_BUDGET;
        assertTrue(ratio > 0.9d && ratio < 1.1d, "pixels=" + pixels + " ratio=" + ratio);
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
    void paperMarginIsCroppedButTableStaysWhole() {
        BufferedImage src = whiteImage(400, 500);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(80, 100, 240, 280);
        g.dispose();
        BufferedImage cropped = RecognitionImageEnhanceSupport.cropPaperMargin(src);
        assertTrue(cropped.getWidth() < 400, "width=" + cropped.getWidth());
        assertTrue(cropped.getHeight() < 500, "height=" + cropped.getHeight());
        assertTrue(cropped.getWidth() > 240);
        assertTrue(cropped.getHeight() > 280);
    }

    @Test
    void fitToModelPixelBudgetScalesUpAndDown() {
        BufferedImage small = whiteImage(400, 300);
        BufferedImage up = RecognitionImageEnhanceSupport.fitToModelPixelBudget(small);
        long upPixels = (long) up.getWidth() * (long) up.getHeight();
        assertTrue(upPixels > 400L * 300L);
        assertTrue(Math.abs(upPixels - RecognitionImageEnhanceSupport.MODEL_PIXEL_BUDGET)
                < RecognitionImageEnhanceSupport.MODEL_PIXEL_BUDGET * 0.05d);

        BufferedImage large = whiteImage(2600, 2000);
        BufferedImage down = RecognitionImageEnhanceSupport.fitToModelPixelBudget(large);
        long downPixels = (long) down.getWidth() * (long) down.getHeight();
        assertTrue(downPixels < 2600L * 2000L);
        assertTrue(Math.abs(downPixels - RecognitionImageEnhanceSupport.MODEL_PIXEL_BUDGET)
                < RecognitionImageEnhanceSupport.MODEL_PIXEL_BUDGET * 0.05d);
    }

    @Test
    void smallSkewOfRuledSheetIsDetected() throws Exception {
        BufferedImage sheet = ruledSheet(400, 500);
        BufferedImage tilted = RecognitionImageEnhanceSupport.rotate(sheet, 2.0d);
        double estimated = RecognitionImageEnhanceSupport.estimateDeskewDegrees(tilted);
        assertTrue(Math.abs(estimated - 2.0d) <= 0.6d, "deskew=" + estimated);
    }

    @Test
    void whitenPaperLiftsNearWhiteWithoutCrushingInk() {
        BufferedImage image = whiteImage(40, 40);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(236, 236, 236));
        g.fillRect(0, 0, 40, 40);
        g.setColor(new Color(70, 70, 70));
        g.fillRect(10, 10, 8, 8);
        g.dispose();
        int inkBefore = luminanceAt(image, 12, 12);
        RecognitionImageEnhanceSupport.whitenPaper(image);
        assertTrue(luminanceAt(image, 2, 2) > 245);
        assertTrue(Math.abs(luminanceAt(image, 12, 12) - inkBefore) <= 2);
    }

    @Test
    void boostInkDarkensMidtones() {
        BufferedImage image = whiteImage(20, 20);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(140, 140, 140));
        g.fillRect(0, 0, 20, 20);
        g.dispose();
        int before = luminanceAt(image, 5, 5);
        RecognitionImageEnhanceSupport.boostInk(image);
        assertTrue(luminanceAt(image, 5, 5) < before);
    }

    private static int luminanceAt(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        return (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
    }

    private static BufferedImage whiteImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
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
