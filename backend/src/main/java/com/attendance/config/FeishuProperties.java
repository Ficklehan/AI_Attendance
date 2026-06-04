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
    private String frontendCallbackUrl = "http://localhost:5175/feishu/callback";
    private String frontendLoginUrl = "http://localhost:5175";

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
        return frontendCallbackUrl;
    }

    public void setFrontendCallbackUrl(String frontendCallbackUrl) {
        this.frontendCallbackUrl = frontendCallbackUrl;
    }

    public String getFrontendLoginUrl() {
        return frontendLoginUrl;
    }

    public void setFrontendLoginUrl(String frontendLoginUrl) {
        this.frontendLoginUrl = frontendLoginUrl;
    }
}