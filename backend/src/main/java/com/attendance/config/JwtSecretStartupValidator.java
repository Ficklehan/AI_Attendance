package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Refuses to start in prod-like profiles when JWT secret is missing or too weak.
 */
@Component
public class JwtSecretStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretStartupValidator.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!requiresStrongSecret()) {
            return;
        }
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("生产环境必须配置 JWT_SECRET（至少 32 字节随机字符串）");
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要 32 字节");
        }
        log.info("JWT secret 校验通过");
    }

    private boolean requiresStrongSecret() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile)
                        || "uat".equalsIgnoreCase(profile));
    }
}
