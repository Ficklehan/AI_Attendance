package com.attendance.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 上传文件流水号命名：yyyyMMdd_0001.ext */
public final class UploadSerialNaming {

    private static final Pattern UPLOAD_SERIAL_PATTERN =
            Pattern.compile("^(\\d{8})_(\\d{4})\\.[^./\\\\]+$", Pattern.CASE_INSENSITIVE);

    private static final Object LOCK = new Object();
    private static String uploadSerialDate = "";
    private static int uploadSerialCounter = 0;

    private UploadSerialNaming() {
    }

    public static String allocateFilename(String uploadRoot, String ext) throws IOException {
        String datePrefix = new SimpleDateFormat("yyyyMMdd").format(new Date());
        synchronized (LOCK) {
            if (!datePrefix.equals(uploadSerialDate)) {
                uploadSerialDate = datePrefix;
                uploadSerialCounter = scanMaxUploadSerialForDate(uploadRoot, datePrefix);
            }
            while (uploadSerialCounter >= 9999) {
                throw new IOException("当日上传流水号已达上限（9999）");
            }
            uploadSerialCounter++;
            String filename = String.format(Locale.ROOT, "%s_%04d%s", datePrefix, uploadSerialCounter, ext);
            Path target = Paths.get(uploadRoot, filename);
            if (Files.exists(target)) {
                uploadSerialCounter = scanMaxUploadSerialForDate(uploadRoot, datePrefix);
                uploadSerialCounter++;
                filename = String.format(Locale.ROOT, "%s_%04d%s", datePrefix, uploadSerialCounter, ext);
                target = Paths.get(uploadRoot, filename);
                if (Files.exists(target)) {
                    throw new IOException("上传文件名冲突: " + filename);
                }
            }
            return filename;
        }
    }

    public static String resolveExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return ".jpg";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot >= originalFilename.length() - 1) {
            return ".jpg";
        }
        String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
        if (".jpeg".equals(ext)) {
            return ".jpg";
        }
        return ext;
    }

    private static int scanMaxUploadSerialForDate(String uploadRoot, String datePrefix) {
        File dir = new File(uploadRoot);
        if (!dir.isDirectory()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int max = 0;
        for (File file : files) {
            Matcher matcher = UPLOAD_SERIAL_PATTERN.matcher(file.getName());
            if (!matcher.matches() || !datePrefix.equals(matcher.group(1))) {
                continue;
            }
            int serial = Integer.parseInt(matcher.group(2));
            if (serial > max) {
                max = serial;
            }
        }
        return max;
    }
}
