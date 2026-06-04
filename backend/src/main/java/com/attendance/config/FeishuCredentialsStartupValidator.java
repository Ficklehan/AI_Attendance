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
 * Warns when Feishu credentials are missing on uat/prod-like profiles.
 */
@Component
public class FeishuCredentialsStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeishuCredentialsStartupValidator.class);

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!requiresFeishuCredentials()) {
            return;
        }
        if (isConfigured()) {
            log.info("飞书应用凭证已配置: appId={}", maskAppId(feishuProperties.getAppId()));
            return;
        }
        log.error("============================================================");
        log.error("飞书凭证未配置：小程序登录 / OAuth / 多维表同步将不可用");
        log.error("请在 UAT/生产环境设置环境变量或 backend/.env：");
        log.error("  FEISHU_APP_ID=cli_xxx");
        log.error("  FEISHU_APP_SECRET=xxx");
        String redirectHint = feishuProperties.getRedirectUri();
        if (redirectHint == null || redirectHint.trim().isEmpty()) {
            redirectHint = "（由 deploy/rendered/*.env 提供 FEISHU_REDIRECT_URI）";
        }
        log.error("  FEISHU_REDIRECT_URI={}", redirectHint);
        log.error("生成方式: node scripts/render-deploy-config.mjs --env production");
        log.error("============================================================");
    }

    public boolean isConfigured() {
        String appId = feishuProperties.getAppId();
        String appSecret = feishuProperties.getAppSecret();
        return appId != null && !appId.trim().isEmpty()
                && appSecret != null && !appSecret.trim().isEmpty();
    }

    private boolean requiresFeishuCredentials() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "uat".equalsIgnoreCase(profile)
                        || "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
    }

    public static String maskAppId(String appId) {
        if (appId == null || appId.length() <= 8) {
            return "***";
        }
        return appId.substring(0, 8) + "…";
    }
}
