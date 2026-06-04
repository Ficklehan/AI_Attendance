package com.attendance.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.config.StorageProperties;
import com.attendance.util.UploadPathSecurity;
import com.attendance.util.UploadSerialNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class OssFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(OssFileStorage.class);

    private final StorageProperties properties;
    private final OSS ossClient;

    public OssFileStorage(StorageProperties properties) {
        this.properties = properties;
        if (isBlank(properties.getOssEndpoint()) || isBlank(properties.getOssBucket())
                || isBlank(properties.getOssAccessKeyId()) || isBlank(properties.getOssAccessKeySecret())) {
            throw new IllegalStateException("attendance.storage.type=oss 时必须配置 OSS endpoint/bucket/accessKey");
        }
        this.ossClient = new OSSClientBuilder().build(
                properties.getOssEndpoint().trim(),
                properties.getOssAccessKeyId().trim(),
                properties.getOssAccessKeySecret().trim());
        log.info("已启用 OSS 文件存储: bucket={}", properties.getOssBucket());
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public String save(byte[] data, String originalFilename) throws IOException {
        String ext = UploadSerialNaming.resolveExtension(originalFilename);
        String filename = UploadSerialNaming.allocateFilename("./uploads", ext);
        String objectKey = objectKey(filename);
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(data.length);
        ossClient.putObject(properties.getOssBucket(), objectKey, new ByteArrayInputStream(data), meta);
        log.info("OSS 上传完成: key={}", objectKey);
        return filename;
    }

    @Override
    public byte[] readBytes(String fileKey) throws IOException {
        UploadPathSecurity.validateFileKey(fileKey);
        try {
            return com.aliyun.oss.common.utils.IOUtils.readStreamAsByteArray(
                    ossClient.getObject(properties.getOssBucket(), objectKey(fileKey)).getObjectContent());
        } catch (Exception e) {
            throw new IOException("OSS 读取失败: " + fileKey, e);
        }
    }

    @Override
    public boolean exists(String fileKey) {
        UploadPathSecurity.validateFileKey(fileKey);
        return ossClient.doesObjectExist(properties.getOssBucket(), objectKey(fileKey));
    }

    @Override
    public void delete(String fileKey) {
        UploadPathSecurity.validateFileKey(fileKey);
        ossClient.deleteObject(properties.getOssBucket(), objectKey(fileKey));
    }

    @Override
    public Optional<Path> resolveLocalPath(String fileKey) {
        return Optional.empty();
    }

    @Override
    public String publicAccessPath(String fileKey) {
        UploadPathSecurity.validateFileKey(fileKey);
        String base = properties.getOssPublicBaseUrl();
        if (base != null && !base.trim().isEmpty()) {
            String normalized = base.trim();
            if (!normalized.endsWith("/")) {
                normalized += "/";
            }
            String prefix = properties.getOssKeyPrefix() != null ? properties.getOssKeyPrefix().trim() : "";
            return normalized + prefix + fileKey;
        }
        throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND);
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    private String objectKey(String fileKey) {
        String prefix = properties.getOssKeyPrefix() != null ? properties.getOssKeyPrefix().trim() : "";
        if (prefix.isEmpty()) {
            return fileKey;
        }
        if (prefix.endsWith("/")) {
            return prefix + fileKey;
        }
        return prefix + "/" + fileKey;
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
