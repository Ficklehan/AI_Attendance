package com.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {
    private String appId;
    private String appSecret;
    private String encryptionKey;
    private String verificationToken;
    private String redirectUri;
    private String frontendCallbackUrl = "http://localhost:5175/attendance/feishu/callback";
    private String frontendLoginUrl = "http://localhost:5175/attendance/";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getFrontendCallbackUrl() {
        return normalizeFrontendCallbackUrl(frontendCallbackUrl);
    }

    public void setFrontendCallbackUrl(String frontendCallbackUrl) {
        this.frontendCallbackUrl = frontendCallbackUrl;
    }

    public String getFrontendLoginUrl() {
        return normalizeFrontendLoginUrl(frontendLoginUrl);
    }

    /**
     * 兼容旧版 render：{@code https://host/feishu/callback} → {@code .../attendance/feishu/callback}
     *（与 frontend vite base /attendance/ 一致）
     */
    static String normalizeFrontendCallbackUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.contains("/attendance/feishu/callback")) {
            return url;
        }
        if (url.contains("/feishu/callback")) {
            return url.replace("/feishu/callback", "/attendance/feishu/callback");
        }
        return url;
    }

    /** 兼容旧版仅配置站点根域名的登录跳转 */
    static String normalizeFrontendLoginUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.contains("/attendance")) {
            return url;
        }
        if (url.matches("https?://[^/]+/?$")) {
            return url.replaceAll("/+$", "") + "/attendance/";
        }
        return url;
    }

    public void setFrontendLoginUrl(String frontendLoginUrl) {
        this.frontendLoginUrl = frontendLoginUrl;
    }
}