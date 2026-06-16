package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.ImageQualityConfigDTO;
import com.attendance.service.UploadMediaSupport;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * PC / 小程序 / 聊天 上传共用：拒绝空文件与非图片/PDF，避免模型无图时编造提示词示例数据。
 */
public final class ImageUploadValidator {

    private static final int MIN_BYTES = 1024;
    private static final int MIN_PDF_BYTES = 256;

    private ImageUploadValidator() {
    }

    public static void validate(byte[] fileBytes, String originalFilename, String contentType, Logger log) {
        validate(fileBytes, originalFilename, contentType, log, null);
    }

    public static void validate(byte[] fileBytes, String originalFilename, String contentType, Logger log,
                                ImageQualityConfigDTO imageQualityConfig) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_SMALL,
                    Collections.singletonMap("size", 0));
        }
        boolean pdf = isPdfUpload(fileBytes, originalFilename, contentType);
        int minBytes = pdf ? MIN_PDF_BYTES : MIN_BYTES;
        if (fileBytes.length < minBytes) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_SMALL,
                    Collections.singletonMap("size", fileBytes.length));
        }
        if (contentType != null && !contentType.trim().isEmpty()) {
            String ct = contentType.toLowerCase();
            if (!ct.startsWith("image/") && !ct.contains("pdf")) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGES_ONLY);
            }
        }
        if (pdf) {
            if (!PdfToImageConverter.looksLikePdfBytes(fileBytes)) {
                if (log != null) {
                    log.warn("文件头不像 PDF: name={}, size={}", originalFilename, fileBytes.length);
                }
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UNRECOGNIZED_IMAGE_FORMAT);
            }
            validateSharpness(fileBytes, originalFilename, contentType, log, imageQualityConfig);
            return;
        }
        if (!looksLikeImageBytes(fileBytes)) {
            if (log != null) {
                log.warn("文件头不像图片: name={}, size={}", originalFilename, fileBytes.length);
            }
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UNRECOGNIZED_IMAGE_FORMAT);
        }
        validateSharpness(fileBytes, originalFilename, contentType, log, imageQualityConfig);
    }

    /**
     * Validate after pages are rendered once (PDF sharpness uses first JPEG page, no second PdfToImageConverter pass).
     */
    public static void validatePages(List<UploadMediaSupport.ImagePage> pages,
                                     byte[] originalBytes,
                                     String originalFilename,
                                     String contentType,
                                     Logger log,
                                     ImageQualityConfigDTO imageQualityConfig) {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_SMALL,
                    Collections.singletonMap("size", 0));
        }
        boolean pdf = isPdfUpload(originalBytes, originalFilename, contentType);
        int minBytes = pdf ? MIN_PDF_BYTES : MIN_BYTES;
        if (originalBytes.length < minBytes) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_SMALL,
                    Collections.singletonMap("size", originalBytes.length));
        }
        if (contentType != null && !contentType.trim().isEmpty()) {
            String ct = contentType.toLowerCase();
            if (!ct.startsWith("image/") && !ct.contains("pdf")) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGES_ONLY);
            }
        }
        if (pages == null || pages.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UNRECOGNIZED_IMAGE_FORMAT);
        }
        if (pdf) {
            if (!PdfToImageConverter.looksLikePdfBytes(originalBytes)) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UNRECOGNIZED_IMAGE_FORMAT);
            }
            UploadMediaSupport.ImagePage first = pages.get(0);
            validateSharpness(first.getBytes(), first.getLabel(), "image/jpeg", log, imageQualityConfig);
            return;
        }
        if (!looksLikeImageBytes(originalBytes)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UNRECOGNIZED_IMAGE_FORMAT);
        }
        validateSharpness(originalBytes, originalFilename, contentType, log, imageQualityConfig);
    }

    /**
     * 上传前锐度预检：整图 Laplacian 方差过低则拒绝，避免浪费识别配额。
     */
    public static void validateSharpness(byte[] fileBytes, String originalFilename, String contentType, Logger log) {
        validateSharpness(fileBytes, originalFilename, contentType, log, null);
    }

    public static void validateSharpness(byte[] fileBytes, String originalFilename, String contentType, Logger log,
                                         ImageQualityConfigDTO imageQualityConfig) {
        if (imageQualityConfig != null
                && (!imageQualityConfig.isEnabled() || !imageQualityConfig.isPreUploadSharpnessEnabled())) {
            return;
        }
        double threshold = imageQualityConfig != null
                ? imageQualityConfig.getMinLaplacianVariance()
                : ImageSharpnessAnalyzer.MIN_LAPLACIAN_VARIANCE;
        Double variance = ImageSharpnessAnalyzer.measureLaplacianVariance(
                fileBytes, originalFilename, contentType, log);
        boolean checkEnabled = imageQualityConfig == null
                || (imageQualityConfig.isEnabled() && imageQualityConfig.isPreUploadSharpnessEnabled());
        if (variance == null) {
            if (checkEnabled) {
                if (log != null) {
                    log.warn("上传锐度预检失败（无法分析图片），拒绝上传: file={}", originalFilename);
                }
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_BLURRY,
                        buildSharpnessArgs(-1, threshold));
            }
            return;
        }
        if (variance < threshold) {
            if (log != null) {
                log.warn("上传锐度预检拒绝: variance={}, threshold={}, file={}",
                        variance, threshold, originalFilename);
            }
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_BLURRY,
                    buildSharpnessArgs(variance, threshold));
        }
        if (log != null) {
            log.info("上传锐度预检通过: variance={}, threshold={}, file={}",
                    variance, threshold, originalFilename);
        }
    }

    private static Map<String, Object> buildSharpnessArgs(double variance, double threshold) {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("layer", com.attendance.dto.ImageQualityAssessment.LAYER_IMAGE);
        args.put("variance", variance < 0 ? 0 : (int) Math.round(variance));
        args.put("threshold", (int) Math.round(threshold));
        return args;
    }

    public static boolean isPdfUpload(byte[] fileBytes, String originalFilename, String contentType) {
        if (PdfToImageConverter.looksLikePdfBytes(fileBytes)) {
            return true;
        }
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        return PdfToImageConverter.filenameLooksLikePdf(originalFilename);
    }

    public static boolean looksLikeImageBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return true;
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return true;
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return true;
        }
        return false;
    }
}
