package com.attendance.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 上传文件存储（本地磁盘或对象存储）。
 */
public interface FileStorage {

    String save(byte[] data, String originalFilename) throws IOException;

    byte[] readBytes(String fileKey) throws IOException;

    boolean exists(String fileKey);

    void delete(String fileKey) throws IOException;

    /** 本地可读路径；对象存储模式返回 empty */
    Optional<Path> resolveLocalPath(String fileKey);

    /**
     * 对外访问 URL。本地模式返回应用内 /local/image 路径后缀；OSS 返回公网/CDN URL。
     */
    String publicAccessPath(String fileKey);

    boolean isRemote();
}
