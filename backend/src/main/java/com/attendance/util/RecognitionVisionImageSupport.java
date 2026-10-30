package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * DeepSeek Flash 识图预处理：每张图进模型前会被缩到约 1300×1300。
 * 因此大表用「整页 + 重叠分条」多图请求，让细字吃满分辨率预算。
 */
public final class RecognitionVisionImageSupport {

    public static final String DETAIL_HIGH = "high";

    static final int MODEL_PIXEL_BUDGET = 1300 * 1300;
    static final int MIN_LONG_EDGE_TO_TILE = 1400;
    static final int MAX_STRIPS = 4;
    static final int MAX_TOTAL_RAW_BYTES = 18 * 1024 * 1024;
    static final float JPEG_QUALITY = 0.92f;
    static final double STRIP_OVERLAP = 0.18d;

    public static final String MULTI_IMAGE_GUIDE =
            "【图片说明】第1张是整页签到表；其后是从上到下（或从左到右）的高清分条，条带有重叠。"
                    + "请识别整张表的全部行，分条只用于看清细字；同一人同一行不要重复输出。";

    private RecognitionVisionImageSupport() {
    }

    public static final class VisionImagePart {
        private final String mimeType;
        private final String base64;
        private final String kind;
        private final int width;
        private final int height;
        private final int rawBytes;

        public VisionImagePart(String mimeType, String base64, String kind, int width, int height, int rawBytes) {
            this.mimeType = mimeType;
            this.base64 = base64;
            this.kind = kind;
            this.width = width;
            this.height = height;
            this.rawBytes = rawBytes;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getBase64() {
            return base64;
        }

        public String getKind() {
            return kind;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getRawBytes() {
            return rawBytes;
        }
    }

    public static List<VisionImagePart> buildParts(byte[] imageBytes, String mimeType, boolean tile) {
        if (!tile) {
            VisionImagePart overview = overviewPart(imageBytes, mimeType);
            return overview == null ? Collections.<VisionImagePart>emptyList()
                    : Collections.singletonList(overview);
        }
        // 分条：概览压到模型预算，条带从高清原图裁，避免整页先缩再切导致细字糊掉
        VisionImagePart overview = overviewPartWithinBudget(imageBytes, mimeType);
        if (overview == null) {
            return Collections.emptyList();
        }
        List<VisionImagePart> strips = buildStrips(imageBytes, overview);
        if (strips.isEmpty()) {
            return Collections.singletonList(overview);
        }
        List<VisionImagePart> parts = new ArrayList<VisionImagePart>(1 + strips.size());
        parts.add(overview);
        parts.addAll(strips);
        return parts;
    }

    /** 整页概览：总像素贴近 Flash 预算，供模型建立全局表结构。 */
    private static VisionImagePart overviewPartWithinBudget(byte[] imageBytes, String mimeType) {
        BufferedImage src = readImage(imageBytes);
        if (src == null) {
            return overviewPart(imageBytes, mimeType);
        }
        long pixels = (long) src.getWidth() * (long) src.getHeight();
        if (pixels <= 0) {
            return overviewPart(imageBytes, mimeType);
        }
        double scale = Math.sqrt(MODEL_PIXEL_BUDGET / (double) pixels);
        if (Math.abs(scale - 1.0d) < 0.02d) {
            return overviewPart(imageBytes, mimeType);
        }
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        byte[] jpeg = encodeJpeg(scaled);
        if (jpeg == null || jpeg.length == 0) {
            return overviewPart(imageBytes, mimeType);
        }
        return new VisionImagePart(
                "image/jpeg",
                Base64.getEncoder().encodeToString(jpeg),
                "overview",
                w,
                h,
                jpeg.length);
    }

    public static String withMultiImageGuide(String aiPrompt, int imageCount) {
        if (imageCount <= 1 || aiPrompt == null) {
            return aiPrompt;
        }
        return MULTI_IMAGE_GUIDE + "\n\n" + aiPrompt;
    }

    public static JSONObject toImageUrlBlock(VisionImagePart part) {
        JSONObject imageUrlObj = new JSONObject();
        imageUrlObj.put("url", "data:" + part.getMimeType() + ";base64," + part.getBase64());
        imageUrlObj.put("detail", DETAIL_HIGH);
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrlObj);
        return imagePart;
    }

    static int stripCount(int width, int height) {
        int longEdge = Math.max(width, height);
        if (longEdge < MIN_LONG_EDGE_TO_TILE || (long) width * height <= MODEL_PIXEL_BUDGET) {
            return 0;
        }
        int n = (int) Math.ceil(longEdge / 1300.0d);
        n = Math.max(2, Math.min(MAX_STRIPS, n));
        return n;
    }

    private static VisionImagePart overviewPart(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        String safeMime = mimeType == null || mimeType.trim().isEmpty() ? "image/jpeg" : mimeType.trim();
        int[] size = readSize(imageBytes);
        return new VisionImagePart(
                safeMime,
                Base64.getEncoder().encodeToString(imageBytes),
                "overview",
                size == null ? -1 : size[0],
                size == null ? -1 : size[1],
                imageBytes.length);
    }

    private static List<VisionImagePart> buildStrips(byte[] imageBytes, VisionImagePart overview) {
        BufferedImage src = readImage(imageBytes);
        if (src == null) {
            return Collections.emptyList();
        }
        int width = src.getWidth();
        int height = src.getHeight();
        int n = stripCount(width, height);
        if (n < 2) {
            return Collections.emptyList();
        }
        boolean portrait = height >= width;
        int longEdge = portrait ? height : width;
        int stripSpan = stripSpan(longEdge, n);
        int step = n <= 1 ? longEdge : (int) Math.round((longEdge - stripSpan) / (double) (n - 1));
        if (step < 1) {
            step = 1;
        }

        List<VisionImagePart> strips = new ArrayList<VisionImagePart>(n);
        int usedBytes = overview.getRawBytes();
        for (int i = 0; i < n; i++) {
            int start = Math.min(i * step, longEdge - stripSpan);
            if (start < 0) {
                start = 0;
            }
            int end = Math.min(start + stripSpan, longEdge);
            if (end <= start) {
                continue;
            }
            BufferedImage crop = portrait
                    ? copyRgb(src, 0, start, width, end - start)
                    : copyRgb(src, start, 0, end - start, height);
            byte[] jpeg = encodeJpeg(crop);
            if (jpeg == null || jpeg.length == 0) {
                continue;
            }
            if (usedBytes + jpeg.length > MAX_TOTAL_RAW_BYTES) {
                break;
            }
            usedBytes += jpeg.length;
            strips.add(new VisionImagePart(
                    "image/jpeg",
                    Base64.getEncoder().encodeToString(jpeg),
                    "strip",
                    crop.getWidth(),
                    crop.getHeight(),
                    jpeg.length));
        }
        return strips.size() >= 2 ? strips : Collections.<VisionImagePart>emptyList();
    }

    static int stripSpan(int longEdge, int stripCount) {
        double divisor = stripCount - (stripCount - 1) * STRIP_OVERLAP;
        if (divisor <= 0) {
            return longEdge;
        }
        int span = (int) Math.ceil(longEdge / divisor);
        return Math.max(1, Math.min(longEdge, span));
    }

    private static BufferedImage readImage(byte[] imageBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] readSize(byte[] imageBytes) {
        BufferedImage image = readImage(imageBytes);
        if (image == null) {
            return null;
        }
        return new int[]{image.getWidth(), image.getHeight()};
    }

    private static BufferedImage copyRgb(BufferedImage src, int x, int y, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, x, y, x + w, y + h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return writeFallbackPng(image);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            ImageOutputStream ios = ImageIO.createImageOutputStream(out);
            try {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
                ios.flush();
            } finally {
                ios.close();
            }
            return out.toByteArray();
        } catch (Exception e) {
            return writeFallbackPng(image);
        } finally {
            writer.dispose();
        }
    }

    private static byte[] writeFallbackPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                return null;
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
