package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 PDF 各页渲染为 JPEG，供视觉模型识别。
 */
public final class PdfToImageConverter {

    private static final Logger log = LoggerFactory.getLogger(PdfToImageConverter.class);

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private PdfToImageConverter() {
    }

    public static boolean looksLikePdfBytes(byte[] bytes) {
        return bytes != null
                && bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    public static List<byte[]> toJpegPages(byte[] pdfBytes, int maxPages, float dpi, Logger logRef) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.PDF_CONVERT_FAILED);
        }
        Logger logger = logRef != null ? logRef : log;
        logger.info("开始解析 PDF，大小 {} bytes", pdfBytes.length);
        List<byte[]> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.PDF_EMPTY);
            }
            if (pageCount > maxPages) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.PDF_TOO_MANY_PAGES,
                        Map.of("pages", pageCount, "maxPages", maxPages));
            }
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                pages.add(toJpegBytes(image));
            }
            logger.info("PDF 转图片完成: pages={}, dpi={}", pageCount, dpi);
            return pages;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            logger.warn("PDF 解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.PDF_CONVERT_FAILED);
        }
    }

    private static byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IOException("JPEG 编码失败");
        }
        return out.toByteArray();
    }

    public static String pageLabel(String originalFilename, int pageIndex, int pageCount) {
        String base = stripExtension(originalFilename);
        if (pageCount <= 1) {
            return base;
        }
        return base + "_p" + pageIndex + ".jpg";
    }

    public static String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            return filename.substring(0, dot);
        }
        return filename;
    }

    public static boolean filenameLooksLikePdf(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}
