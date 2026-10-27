package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionVisionImageSupportTest {

    @Test
    void smallImageStaysSinglePart() throws Exception {
        byte[] png = png(400, 400);
        List<RecognitionVisionImageSupport.VisionImagePart> parts =
                RecognitionVisionImageSupport.buildParts(png, "image/png", true);
        assertEquals(1, parts.size());
        assertEquals("overview", parts.get(0).getKind());
        assertEquals(0, RecognitionVisionImageSupport.stripCount(400, 400));
    }

    @Test
    void tallSheetSplitsIntoOverlappingStrips() throws Exception {
        byte[] png = png(800, 2400);
        List<RecognitionVisionImageSupport.VisionImagePart> parts =
                RecognitionVisionImageSupport.buildParts(png, "image/png", true);
        assertTrue(parts.size() >= 3, "expected overview + at least 2 strips, got " + parts.size());
        assertEquals("overview", parts.get(0).getKind());
        assertEquals("image/png", parts.get(0).getMimeType());
        assertEquals(800, parts.get(0).getWidth());
        assertEquals(2400, parts.get(0).getHeight());
        for (int i = 1; i < parts.size(); i++) {
            assertEquals("strip", parts.get(i).getKind());
            assertEquals("image/jpeg", parts.get(i).getMimeType());
            assertEquals(800, parts.get(i).getWidth());
            assertTrue(parts.get(i).getHeight() >= 900);
            assertTrue(parts.get(i).getHeight() <= 2400);
        }
    }

    @Test
    void landscapeSheetSplitsAlongWidth() throws Exception {
        byte[] png = png(2400, 800);
        List<RecognitionVisionImageSupport.VisionImagePart> parts =
                RecognitionVisionImageSupport.buildParts(png, "image/png", true);
        assertTrue(parts.size() >= 3);
        assertEquals("overview", parts.get(0).getKind());
        for (int i = 1; i < parts.size(); i++) {
            assertEquals(800, parts.get(i).getHeight());
            assertTrue(parts.get(i).getWidth() >= 900);
        }
    }

    @Test
    void stripCountIsCapped() {
        assertEquals(4, RecognitionVisionImageSupport.stripCount(1200, 4000));
        assertEquals(2, RecognitionVisionImageSupport.stripCount(800, 2400));
        assertEquals(0, RecognitionVisionImageSupport.stripCount(1300, 1300));
    }

    @Test
    void tileDisabledKeepsOriginalOnly() throws Exception {
        byte[] png = png(800, 2400);
        List<RecognitionVisionImageSupport.VisionImagePart> parts =
                RecognitionVisionImageSupport.buildParts(png, "image/png", false);
        assertEquals(1, parts.size());
        assertEquals("overview", parts.get(0).getKind());
    }

    @Test
    void imageUrlBlockSetsHighDetail() throws Exception {
        byte[] png = png(64, 64);
        RecognitionVisionImageSupport.VisionImagePart part =
                RecognitionVisionImageSupport.buildParts(png, "image/png", false).get(0);
        JSONObject block = RecognitionVisionImageSupport.toImageUrlBlock(part);
        assertEquals("image_url", block.getString("type"));
        JSONObject imageUrl = block.getJSONObject("image_url");
        assertEquals(RecognitionVisionImageSupport.DETAIL_HIGH, imageUrl.getString("detail"));
        assertTrue(imageUrl.getString("url").startsWith("data:image/png;base64,"));
    }

    @Test
    void multiImageGuideOnlyWhenTiled() {
        assertEquals("PROMPT", RecognitionVisionImageSupport.withMultiImageGuide("PROMPT", 1));
        String guided = RecognitionVisionImageSupport.withMultiImageGuide("PROMPT", 3);
        assertTrue(guided.startsWith(RecognitionVisionImageSupport.MULTI_IMAGE_GUIDE));
        assertTrue(guided.endsWith("PROMPT"));
        assertNull(RecognitionVisionImageSupport.withMultiImageGuide(null, 3));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.DARK_GRAY);
        g.drawRect(8, 8, width - 17, height - 17);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return out.toByteArray();
    }
}
