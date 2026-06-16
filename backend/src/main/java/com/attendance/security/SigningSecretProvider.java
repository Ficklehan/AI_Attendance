package com.attendance.security;

import com.attendance.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * Central HMAC/JWT signing secret. Prod/uat requires configured JWT_SECRET;
 * dev generates an ephemeral random secret per process when unset.
 */
@Component
public class SigningSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(SigningSecretProvider.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final Environment environment;
    private byte[] secretBytes;

    public SigningSecretProvider(JwtProperties jwtProperties, Environment environment) {
        this.jwtProperties = jwtProperties;
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            if (requiresStrongSecret()) {
                throw new IllegalStateException("生产环境必须配置 JWT_SECRET（至少 32 字节随机字符串）");
            }
            secret = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            jwtProperties.setSecret(secret);
            log.warn("JWT_SECRET 未配置，已生成本次启动随机密钥（重启后失效，仅用于本地开发）");
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (requiresStrongSecret() && raw.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要 32 字节");
        }
        if (raw.length < MIN_SECRET_BYTES) {
            byte[] padded = new byte[MIN_SECRET_BYTES];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            raw = padded;
        }
        this.secretBytes = raw;
    }

    public byte[] getSecretBytes() {
        return secretBytes;
    }

    public String getSecret() {
        return jwtProperties.getSecret();
    }

    private boolean requiresStrongSecret() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile)
                        || "uat".equalsIgnoreCase(profile));
    }
}
