package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 解析 base-config 目录：兼容从 backend/ 或仓库根目录启动。
 */
@Component
public class ConfigPathResolver {

    private static final Logger log = LoggerFactory.getLogger(ConfigPathResolver.class);
    private static final List<String> REQUIRED_FILES = Arrays.asList(
            "prompts.md",
            "feishu.md",
            "countries.md"
    );

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
            if (candidate != null && hasRequiredFiles(candidate)) {
                return candidate.normalize();
            }
        }

        Path bootstrapTarget = cwd.resolve("base-config").normalize();
        if (bootstrapFromClasspath(bootstrapTarget)) {
            return bootstrapTarget;
        }
        return cwd.resolve("../base-config").normalize();
    }

    private static boolean hasRequiredFiles(Path dir) {
        for (String filename : REQUIRED_FILES) {
            if (!Files.isRegularFile(dir.resolve(filename))) {
                return false;
            }
        }
        return true;
    }

    private static boolean bootstrapFromClasspath(Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            ClassLoader cl = ConfigPathResolver.class.getClassLoader();
            boolean copiedAny = false;
            for (String filename : REQUIRED_FILES) {
                Path targetFile = targetDir.resolve(filename);
                if (Files.exists(targetFile)) {
                    continue;
                }
                try (InputStream in = cl.getResourceAsStream("base-config/" + filename)) {
                    if (in == null) {
                        log.warn("classpath 未找到内置配置: base-config/{}", filename);
                        continue;
                    }
                    Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    copiedAny = true;
                }
            }
            if (hasRequiredFiles(targetDir)) {
                if (copiedAny) {
                    log.info("已从 classpath 自动初始化 base-config 到 {}", targetDir.toAbsolutePath());
                }
                return true;
            }
        } catch (IOException e) {
            log.error("从 classpath 初始化 base-config 失败", e);
        }
        return false;
    }
}
