package com.attendance.storage;

import com.attendance.config.StorageProperties;
import com.attendance.util.UploadPathSecurity;
import com.attendance.util.UploadSerialNaming;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class LocalFileStorage implements FileStorage {

    private final Path uploadRoot;

    public LocalFileStorage(StorageProperties properties) {
        this.uploadRoot = Paths.get(properties.getLocalPath()).toAbsolutePath().normalize();
    }

    @Override
    public String save(byte[] data, String originalFilename) throws IOException {
        Files.createDirectories(uploadRoot);
        String ext = UploadSerialNaming.resolveExtension(originalFilename);
        String filename = UploadSerialNaming.allocateFilename(uploadRoot.toString(), ext);
        Files.write(uploadRoot.resolve(filename), data);
        return filename;
    }

    @Override
    public byte[] readBytes(String fileKey) throws IOException {
        Path path = resolveLocalPath(fileKey).orElseThrow(() -> new IOException("文件不存在: " + fileKey));
        return Files.readAllBytes(path);
    }

    @Override
    public boolean exists(String fileKey) {
        return resolveLocalPath(fileKey).map(Files::exists).orElse(false);
    }

    @Override
    public void delete(String fileKey) throws IOException {
        Path path = resolveLocalPath(fileKey).orElse(null);
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    @Override
    public Optional<Path> resolveLocalPath(String fileKey) {
        UploadPathSecurity.validateFileKey(fileKey);
        Path file = uploadRoot.resolve(fileKey.trim()).normalize();
        if (!file.startsWith(uploadRoot)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    @Override
    public String publicAccessPath(String fileKey) {
        return "/local/image/" + fileKey;
    }

    @Override
    public boolean isRemote() {
        return false;
    }
}
