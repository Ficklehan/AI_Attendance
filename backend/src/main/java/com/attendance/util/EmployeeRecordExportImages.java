package com.attendance.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.attendance.config.StorageProperties;
import com.attendance.security.ImageAccessSignatureService;
import com.attendance.storage.FileStorage;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class EmployeeRecordExportImages {

    /** Excel 单元格内展示的最大宽高（保持比例，不拉伸占满大行） */
    private static final int DISPLAY_MAX_WIDTH = 128;
    private static final int DISPLAY_MAX_HEIGHT = 96;
    /** 单列默认展示宽度：单张图 + 左右留白，用于列宽估算 */
    public static final int THUMB_COLUMN_DEFAULT_WIDTH_PX = DISPLAY_MAX_WIDTH + 12;
    /** 仅当原图极大时才压缩入库，避免 Excel 体积失控；日常考勤照直接嵌原图 */
    private static final int MAX_EMBED_DIMENSION = 1920;
    private static final float EMBED_JPEG_QUALITY = 0.92f;
    private static final int MAX_THUMBNAILS_PER_ROW = 8;

    @Value("${export.public-base-url:http://localhost:8080/attendance/api}")
    private String publicBaseUrl;

    @Autowired
    private ImageAccessSignatureService imageAccessSignatureService;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private StorageProperties storageProperties;

    public static final class ExportImage {
        private final byte[] data;
        private final int pictureType;
        private final int displayWidthPx;
        private final int displayHeightPx;

        public ExportImage(byte[] data, int pictureType, int displayWidthPx, int displayHeightPx) {
            this.data = data;
            this.pictureType = pictureType;
            this.displayWidthPx = displayWidthPx;
            this.displayHeightPx = displayHeightPx;
        }

        public byte[] getData() {
            return data;
        }

        public int getPictureType() {
            return pictureType;
        }

        public int getDisplayWidthPx() {
            return displayWidthPx;
        }

        public int getDisplayHeightPx() {
            return displayHeightPx;
        }
    }

    public int getMaxThumbnailsPerRow() {
        return MAX_THUMBNAILS_PER_ROW;
    }

    public int getDisplayMaxWidth() {
        return DISPLAY_MAX_WIDTH;
    }

    public int getDisplayMaxHeight() {
        return DISPLAY_MAX_HEIGHT;
    }

    public List<String> collectImageKeys(String imageUrlsJson, String fileKey) {
        Set<String> keys = new LinkedHashSet<>();
        if (fileKey != null && !fileKey.trim().isEmpty()) {
            keys.add(fileKey.trim());
        }
        if (imageUrlsJson != null && !imageUrlsJson.trim().isEmpty()) {
            try {
                JSONArray parsed = JSON.parseArray(imageUrlsJson);
                if (parsed != null) {
                    for (int i = 0; i < parsed.size(); i++) {
                        String key = parsed.getString(i);
                        if (key != null && !key.trim().isEmpty()) {
                            keys.add(key.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
                // ignore malformed JSON
            }
        }
        return filterResolvableFileKeys(new ArrayList<>(keys));
    }

    public List<String> filterResolvableFileKeys(List<String> fileKeys) {
        Path uploadRoot = Paths.get(storageProperties.getLocalPath()).toAbsolutePath().normalize();
        return UploadPathSecurity.filterResolvableFileKeys(uploadRoot, fileKeys);
    }

    public List<String> buildSignedImageUrls(List<String> fileKeys, String userId, long expEpochSecond) {
        List<String> urls = new ArrayList<>();
        if (fileKeys == null || fileKeys.isEmpty() || userId == null || userId.trim().isEmpty()) {
            return urls;
        }
        for (String fileKey : fileKeys) {
            if (fileKey == null || fileKey.trim().isEmpty()) {
                continue;
            }
            String trimmed = fileKey.trim();
            String baseUrl = resolveImageBaseUrl(trimmed);
            urls.add(imageAccessSignatureService.appendSignedQueryWithExpiry(baseUrl, trimmed, userId, expEpochSecond));
        }
        return urls;
    }

    /**
     * 读取原图嵌入 Excel：保留原图清晰度，仅在单元格内按适中尺寸展示。
     */
    public ExportImage readExportImage(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            return null;
        }
        String trimmed = fileKey.trim();
        if (!fileStorage.exists(trimmed)) {
            return null;
        }
        try {
            byte[] raw = fileStorage.readBytes(trimmed);
            if (raw == null || raw.length == 0) {
                return null;
            }
            int[] size = readImageSize(raw);
            if (size == null) {
                return null;
            }
            int srcWidth = size[0];
            int srcHeight = size[1];
            byte[] embedData = raw;
            int pictureType = detectPictureType(trimmed, raw);
            if (Math.max(srcWidth, srcHeight) > MAX_EMBED_DIMENSION) {
                byte[] scaled = scaleImageToMax(raw, MAX_EMBED_DIMENSION, pictureType);
                if (scaled == null) {
                    return null;
                }
                embedData = scaled;
                int[] scaledSize = readImageSize(embedData);
                if (scaledSize == null) {
                    return null;
                }
                srcWidth = scaledSize[0];
                srcHeight = scaledSize[1];
                pictureType = detectPictureType(trimmed, embedData);
            }
            int[] display = fitDisplaySize(srcWidth, srcHeight, DISPLAY_MAX_WIDTH, DISPLAY_MAX_HEIGHT);
            return new ExportImage(embedData, pictureType, display[0], display[1]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveImageBaseUrl(String fileKey) {
        String remote = fileStorage.publicAccessPath(fileKey);
        if (remote != null && (remote.startsWith("http://") || remote.startsWith("https://"))) {
            return remote;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = remote != null && remote.startsWith("/") ? remote : "/local/image/" + encodePath(fileKey);
        return base + path;
    }

    private static String encodePath(String fileKey) {
        try {
            return URLEncoder.encode(fileKey, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            return fileKey;
        }
    }

    static int[] readImageSize(byte[] imageBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(imageBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    static int[] fitDisplaySize(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
        if (srcWidth <= 0 || srcHeight <= 0) {
            return new int[]{maxWidth, maxHeight};
        }
        double scale = Math.min((double) maxWidth / srcWidth, (double) maxHeight / srcHeight);
        if (scale > 1d) {
            scale = 1d;
        }
        int width = Math.max(1, (int) Math.round(srcWidth * scale));
        int height = Math.max(1, (int) Math.round(srcHeight * scale));
        return new int[]{width, height};
    }

    static int detectPictureType(String fileKey, byte[] data) {
        if (data != null && data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return Workbook.PICTURE_TYPE_PNG;
        }
        if (fileKey != null && fileKey.toLowerCase().endsWith(".png")) {
            return Workbook.PICTURE_TYPE_PNG;
        }
        return Workbook.PICTURE_TYPE_JPEG;
    }

    static byte[] scaleImageToMax(byte[] imageBytes, int maxDimension, int pictureType) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (src == null) {
            return null;
        }
        double scale = (double) maxDimension / Math.max(src.getWidth(), src.getHeight());
        if (scale >= 1d) {
            return imageBytes;
        }
        int width = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = dst.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(src, 0, 0, width, height, null);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (pictureType == Workbook.PICTURE_TYPE_PNG) {
            ImageIO.write(dst, "png", out);
        } else {
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(EMBED_JPEG_QUALITY);
            }
            try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(dst, null, null), param);
            } finally {
                writer.dispose();
            }
        }
        return out.toByteArray();
    }
}
