package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 解析 base-config 目录：兼容从 backend/ 或仓库根目录启动。
 */
@Component
public class ConfigPathResolver {

    private static final Logger log = LoggerFactory.getLogger(ConfigPathResolver.class);

    private volatile Path baseConfigDir;

    public Path getBaseConfigDir() {
        if (baseConfigDir != null && Files.isDirectory(baseConfigDir)) {
            return baseConfigDir;
        }
        Path resolved = resolve();
        baseConfigDir = resolved;
        log.info("base-config 目录: {}", resolved.toAbsolutePath());
        return resolved;
    }

    public Path resolveFile(String filename) {
        return getBaseConfigDir().resolve(filename);
    }

    private static Path resolve() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path[] candidates = {
                cwd.resolve("base-config"),
                cwd.resolve("../base-config"),
                cwd.resolve("../../base-config"),
                cwd.getParent() != null ? cwd.getParent().resolve("base-config") : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate.resolve("prompts.md"))) {
                return candidate.normalize();
            }
        }
        return cwd.resolve("../base-config").normalize();
    }
}
