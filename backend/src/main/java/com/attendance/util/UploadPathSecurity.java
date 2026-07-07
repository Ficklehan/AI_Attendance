package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates upload file keys and resolves paths under the uploads directory (path traversal defense).
 */
public final class UploadPathSecurity {

    /** yyyyMMdd_0001.jpg — current naming ({@link UploadSerialNaming}) */
    private static final Pattern SAFE_FILE_KEY =
            Pattern.compile("^(\\d{8})_(\\d{3,4})\\.[^./\\\\]+$", Pattern.CASE_INSENSITIVE);

    /** Legacy rows may omit extension in DB while the file on disk has one. */
    private static final Pattern LEGACY_KEY_NO_EXT =
            Pattern.compile("^(\\d{8})_(\\d{3,4})$", Pattern.CASE_INSENSITIVE);

    private static final String[] LEGACY_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".pdf"};

    private UploadPathSecurity() {
    }

    public static void validateFileKey(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGE_INVALID);
        }
        String key = fileKey.trim();
        rejectTraversal(key);
        if (!isSafeFileKeyFormat(key)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
    }

    public static boolean isSafeFileKeyFormat(String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            return false;
        }
        String key = fileKey.trim();
        if (isExternalReference(key)) {
            return false;
        }
        return SAFE_FILE_KEY.matcher(key).matches() || LEGACY_KEY_NO_EXT.matcher(key).matches();
    }

    public static List<String> validateFileKeys(List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return fileKeys;
        }
        Path base = uploadsBase();
        List<String> validated = new ArrayList<>(fileKeys.size());
        for (String fileKey : fileKeys) {
            if (fileKey == null || fileKey.trim().isEmpty()) {
                continue;
            }
            tryCanonicalFileName(base, fileKey).ifPresent(name -> {
                if (!validated.contains(name)) {
                    validated.add(name);
                }
            });
        }
        return validated;
    }

    /** Resolve to the on-disk filename (may add extension for legacy keys). */
    public static String canonicalFileKey(String fileKey) {
        validateFileKey(fileKey);
        return tryCanonicalFileName(uploadsBase(), fileKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH));
    }

    public static Path resolveUploadFile(String fileKey) {
        return tryResolve(uploadsBase(), fileKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH));
    }

    /** Non-throwing resolve for read/export paths; returns empty when key is unsafe or file missing. */
    public static Optional<Path> tryResolve(Path uploadRoot, String fileKey) {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            return Optional.empty();
        }
        String key = fileKey.trim();
        if (isExternalReference(key)) {
            return Optional.empty();
        }
        try {
            rejectTraversal(key);
            if (!isSafeFileKeyFormat(key)) {
                return Optional.empty();
            }
            if (SAFE_FILE_KEY.matcher(key).matches()) {
                Path file = resolveUnderBase(uploadRoot, key);
                return Files.exists(file) ? Optional.of(file) : Optional.empty();
            }
            return tryResolveLegacyNoExt(key, uploadRoot);
        } catch (BusinessException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> tryCanonicalFileName(Path uploadRoot, String fileKey) {
        return tryResolve(uploadRoot, fileKey).map(path -> path.getFileName().toString());
    }

    public static List<String> filterResolvableFileKeys(Path uploadRoot, List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String fileKey : fileKeys) {
            tryCanonicalFileName(uploadRoot, fileKey).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    private static boolean isExternalReference(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || key.contains("/") || key.contains("\\");
    }

    private static void rejectTraversal(String key) {
        if (key.contains("..")) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
    }

    private static Path uploadsBase() {
        return Paths.get("./uploads").toAbsolutePath().normalize();
    }

    private static Path resolveUnderBase(Path base, String key) {
        Path file = base.resolve(key).normalize();
        if (!file.startsWith(base)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
        return file;
    }

    private static Optional<Path> tryResolveLegacyNoExt(String keyWithoutExt, Path base) {
        if (!LEGACY_KEY_NO_EXT.matcher(keyWithoutExt).matches()) {
            return Optional.empty();
        }
        for (String ext : LEGACY_EXTENSIONS) {
            String candidate = keyWithoutExt + ext.toLowerCase(Locale.ROOT);
            Path file = resolveUnderBase(base, candidate);
            if (Files.exists(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }
}
