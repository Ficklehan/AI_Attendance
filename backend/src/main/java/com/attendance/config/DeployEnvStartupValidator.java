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
 * Ensures deploy profile received rendered env vars (FEISHU_* / CORS) before serving traffic.
 */
@Component
public class DeployEnvStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeployEnvStartupValidator.class);

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private CorsProperties corsProperties;

    @Autowired
    private Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!requiresDeployEnv()) {
            return;
        }
        String redirectUri = feishuProperties.getRedirectUri();
        if (isMissing(redirectUri)) {
            fail("FEISHU_REDIRECT_URI 未设置。请先运行 node scripts/render-deploy-config.mjs 并 source deploy/rendered/*.env");
        }
        if (isMissing(feishuProperties.getFrontendCallbackUrl())) {
            fail("FEISHU_FRONTEND_CALLBACK_URL 未设置");
        }
        if (isMissing(feishuProperties.getFrontendLoginUrl())) {
            fail("FEISHU_FRONTEND_LOGIN_URL 未设置");
        }
        if (corsProperties.getAllowedOriginPatterns().isEmpty()
                || corsProperties.getAllowedOriginPatterns().stream().anyMatch(this::isMissing)) {
            fail("CORS_ALLOWED_ORIGIN 未设置");
        }
        log.info("Deploy env 校验通过: redirectUri={}", redirectUri);
    }

    private boolean requiresDeployEnv() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile)
                        || "uat".equalsIgnoreCase(profile));
    }

    private boolean isMissing(String value) {
        return value == null
                || value.trim().isEmpty()
                || value.contains("${");
    }

    private void fail(String message) {
        throw new IllegalStateException(message);
    }
}
