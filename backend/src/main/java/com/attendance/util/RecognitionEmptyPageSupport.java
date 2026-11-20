package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 多页任务：纯白页 / 模型未吐出行 可跳过，不把整单打失败。
 */
public final class RecognitionEmptyPageSupport {

    /** 暗像素占比低于此值视为几乎全白（空表网格约 0.09，实表通常 ≥ 0.06）。 */
    static final double BLANK_DARK_RATIO_MAX = 0.008d;

    static final int DARK_LUMA = 240;

    private RecognitionEmptyPageSupport() {
    }

    public static boolean isNearlyBlank(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return false;
            }
            return darkRatio(image) < BLANK_DARK_RATIO_MAX;
        } catch (Exception e) {
            return false;
        }
    }

    static double darkRatio(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) {
            return 0d;
        }
        int stepX = Math.max(1, w / 200);
        int stepY = Math.max(1, h / 200);
        long dark = 0;
        long total = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                if (luma < DARK_LUMA) {
                    dark++;
                }
                total++;
            }
        }
        return total == 0 ? 0d : dark / (double) total;
    }

    public static boolean isSkippableEmptyPageError(Throwable error) {
        Throwable t = unwrap(error);
        if (t instanceof BusinessException) {
            String key = ((BusinessException) t).getMessageKey();
            return ErrorKeys.AI_NO_PARSEABLE_RECORDS.equals(key)
                    || ErrorKeys.AI_HEADER_ECHO.equals(key)
                    || ErrorKeys.AI_INVALID_JSON.equals(key);
        }
        if (t instanceof IllegalStateException && t.getMessage() != null) {
            String msg = t.getMessage();
            return msg.startsWith("识别结果为空")
                    || msg.startsWith("PDF 识别结果为空")
                    || msg.startsWith("多图识别结果为空");
        }
        return false;
    }

    static Throwable unwrap(Throwable error) {
        Throwable t = error;
        while (t != null && t.getCause() != null
                && (t instanceof CompletionException || t instanceof ExecutionException)) {
            t = t.getCause();
        }
        return t;
    }
}
