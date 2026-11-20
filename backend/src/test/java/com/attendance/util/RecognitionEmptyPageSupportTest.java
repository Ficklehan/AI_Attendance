package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionEmptyPageSupportTest {

    @Test
    void whiteJpegIsNearlyBlank() throws Exception {
        assertTrue(RecognitionEmptyPageSupport.isNearlyBlank(jpeg(white(200, 200))));
    }

    @Test
    void sheetWithInkIsNotBlank() throws Exception {
        BufferedImage image = white(200, 200);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(20, 20, 160, 4);
        g.fillRect(20, 20, 4, 160);
        g.dispose();
        assertFalse(RecognitionEmptyPageSupport.isNearlyBlank(jpeg(image)));
    }

    @Test
    void noParseableRecordsIsSkippable() {
        BusinessException error = new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_NO_PARSEABLE_RECORDS);
        assertTrue(RecognitionEmptyPageSupport.isSkippableEmptyPageError(error));
        assertTrue(RecognitionEmptyPageSupport.isSkippableEmptyPageError(new CompletionException(error)));
        assertTrue(RecognitionEmptyPageSupport.isSkippableEmptyPageError(
                new IllegalStateException("识别结果为空，请检查图片是否清晰。模型回复: 空白")));
    }

    @Test
    void qualityFailuresAreNotSkippable() {
        assertFalse(RecognitionEmptyPageSupport.isSkippableEmptyPageError(
                new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_FABRICATED)));
        assertFalse(RecognitionEmptyPageSupport.isSkippableEmptyPageError(
                new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_MALFORMED_RECORDS)));
        assertFalse(RecognitionEmptyPageSupport.isSkippableEmptyPageError(new IllegalStateException("识别超时")));
    }

    private static BufferedImage white(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static byte[] jpeg(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
