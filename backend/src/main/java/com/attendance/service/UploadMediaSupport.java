package com.attendance.service;

import com.attendance.util.PdfToImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传文件归一化：图片直传，PDF 转为多页 JPEG 后再识别。
 */
@Component
public class UploadMediaSupport {

    private static final Logger log = LoggerFactory.getLogger(UploadMediaSupport.class);

    @Value("${attendance.upload.pdf-max-pages:30}")
    private int pdfMaxPages;

    @Value("${attendance.upload.pdf-render-dpi:200}")
    private float pdfRenderDpi;

    public static final class ImagePage {
        private final byte[] bytes;
        private final String label;

        public ImagePage(byte[] bytes, String label) {
            this.bytes = bytes;
            this.label = label;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getLabel() {
            return label;
        }
    }

    public boolean isPdf(byte[] fileBytes, String originalFilename, String contentType) {
        if (PdfToImageConverter.looksLikePdfBytes(fileBytes)) {
            return true;
        }
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        return PdfToImageConverter.filenameLooksLikePdf(originalFilename);
    }

    public List<ImagePage> toRecognizablePages(byte[] fileBytes, String originalFilename, String contentType) {
        if (isPdf(fileBytes, originalFilename, contentType)) {
            List<byte[]> jpegPages = PdfToImageConverter.toJpegPages(
                    fileBytes, pdfMaxPages, pdfRenderDpi, log);
            List<ImagePage> pages = new ArrayList<>(jpegPages.size());
            for (int i = 0; i < jpegPages.size(); i++) {
                String label = PdfToImageConverter.pageLabel(originalFilename, i + 1, jpegPages.size());
                pages.add(new ImagePage(jpegPages.get(i), label));
            }
            return pages;
        }
        String label = originalFilename != null && !originalFilename.isBlank()
                ? originalFilename
                : "upload.jpg";
        List<ImagePage> single = new ArrayList<>(1);
        single.add(new ImagePage(fileBytes, label));
        return single;
    }
}
