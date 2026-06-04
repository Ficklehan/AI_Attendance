package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates upload file keys and resolves paths under ./uploads only (path traversal defense).
 */
public final class UploadPathSecurity {

    /** Matches keys produced by {@link com.attendance.service.AIParserService#allocateUploadFilename}. */
    private static final Pattern SAFE_FILE_KEY =
            Pattern.compile("^(\\d{8})_(\\d{4})\\.[^./\\\\]+$", Pattern.CASE_INSENSITIVE);

    private UploadPathSecurity() {
    }

    public static void validateFileKey(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGE_INVALID);
        }
        String key = fileKey.trim();
        if (key.contains("..") || key.contains("/") || key.contains("\\")) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
        if (!SAFE_FILE_KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
    }

    public static List<String> validateFileKeys(List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return fileKeys;
        }
        List<String> validated = new ArrayList<>(fileKeys.size());
        for (String fileKey : fileKeys) {
            if (fileKey == null || fileKey.trim().isEmpty()) {
                continue;
            }
            validateFileKey(fileKey);
            String key = fileKey.trim();
            if (!validated.contains(key)) {
                validated.add(key);
            }
        }
        return validated;
    }

    public static Path resolveUploadFile(String fileKey) {
        validateFileKey(fileKey);
        Path base = Paths.get("./uploads").toAbsolutePath().normalize();
        Path file = base.resolve(fileKey.trim()).normalize();
        if (!file.startsWith(base)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
        return file;
    }
}
