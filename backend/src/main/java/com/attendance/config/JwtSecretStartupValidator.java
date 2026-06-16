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
 * Logs JWT secret readiness after {@link com.attendance.security.SigningSecretProvider} initializes.
 */
@Component
public class JwtSecretStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretStartupValidator.class);

    @Autowired
    private com.attendance.security.SigningSecretProvider signingSecretProvider;

    @Autowired
    private Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile)
                        || "uat".equalsIgnoreCase(profile))) {
            log.info("JWT secret 校验通过");
        } else if (signingSecretProvider.getSecret() != null) {
            log.info("开发环境 JWT secret 已就绪");
        }
    }
}
