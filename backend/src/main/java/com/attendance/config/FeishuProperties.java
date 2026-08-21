package com.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {
    /** PC 前端 Vite / History 基路径（站点前缀 /clockai/；业务页如 /records 相对此前缀） */
    public static final String FRONTEND_WEB_BASE = "/clockai";
    public static final String API_CONTEXT_PATH = "/clockai/api";

    private String appId;
    private String appSecret;
    private String encryptionKey;
    private String verificationToken;
    private String redirectUri;
    private String frontendCallbackUrl = "http://localhost:5175/clockai/feishu/callback";
    private String frontendLoginUrl = "http://localhost:5175/clockai/";

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

    /** 从 OAuth 回调地址推导 API 根路径，如 http://host/clockai/api */
    public String getApiBaseUrl() {
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            return "http://localhost:8080" + API_CONTEXT_PATH;
        }
        String uri = redirectUri.trim();
        int idx = uri.indexOf("/feishu-auth/");
        if (idx > 0) {
            return uri.substring(0, idx);
        }
        return uri.replaceAll("/+$", "");
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
     * 兼容旧版 render：{@code https://host/feishu/callback} 或 {@code .../attendance/feishu/callback}
     * → {@code .../clockai/feishu/callback}（与 frontend vite base /clockai/ 一致）
     */
    static String normalizeFrontendCallbackUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.contains("/clockai/feishu/callback")) {
            return url;
        }
        if (url.contains("/attendance/feishu/callback")) {
            return url.replace("/attendance/feishu/callback", "/clockai/feishu/callback");
        }
        if (url.contains("/feishu/callback")) {
            return url.replace("/feishu/callback", "/clockai/feishu/callback");
        }
        return url;
    }

    /** 兼容旧版仅配置站点根域名、或仍使用 /attendance 前缀的登录跳转 */
    static String normalizeFrontendLoginUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.contains("/clockai")) {
            return url;
        }
        if (url.contains("/attendance")) {
            return url.replace("/attendance", "/clockai");
        }
        if (url.matches("https?://[^/]+/?$")) {
            return url.replaceAll("/+$", "") + FRONTEND_WEB_BASE + "/";
        }
        return url;
    }

    public void setFrontendLoginUrl(String frontendLoginUrl) {
        this.frontendLoginUrl = frontendLoginUrl;
    }
}
