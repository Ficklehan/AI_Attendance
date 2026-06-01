package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;

import java.util.Map;
import org.slf4j.Logger;

/**
 * PC / 小程序 / 聊天 上传共用：拒绝空文件与非图片，避免模型无图时编造提示词示例数据。
 */
public final class ImageUploadValidator {

    private static final int MIN_BYTES = 1024;

    private ImageUploadValidator() {
    }

    public static void validate(byte[] fileBytes, String originalFilename, String contentType, Logger log) {
        if (fileBytes == null || fileBytes.length < MIN_BYTES) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UPLOAD_IMAGE_TOO_SMALL,
                    Map.of("size", fileBytes == null ? 0 : fileBytes.length));
        }
        if (contentType != null && !contentType.isBlank() && !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGES_ONLY);
        }
        if (!looksLikeImageBytes(fileBytes)) {
            if (log != null) {
                log.warn("文件头不像图片: name={}, size={}", originalFilename, fileBytes.length);
            }
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.UNRECOGNIZED_IMAGE_FORMAT);
        }
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
