package com.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "attendance.storage")
public class StorageProperties {

    /** local | oss */
    private String type = "local";

    private String localPath = "./uploads";

    private String ossEndpoint = "";

    private String ossBucket = "";

    private String ossAccessKeyId = "";

    private String ossAccessKeySecret = "";

    /** 对象 key 前缀，如 attendance/ */
    private String ossKeyPrefix = "";

    /** 公网访问前缀，如 https://cdn.example.com/attendance/ */
    private String ossPublicBaseUrl = "";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getOssEndpoint() {
        return ossEndpoint;
    }

    public void setOssEndpoint(String ossEndpoint) {
        this.ossEndpoint = ossEndpoint;
    }

    public String getOssBucket() {
        return ossBucket;
    }

    public void setOssBucket(String ossBucket) {
        this.ossBucket = ossBucket;
    }

    public String getOssAccessKeyId() {
        return ossAccessKeyId;
    }

    public void setOssAccessKeyId(String ossAccessKeyId) {
        this.ossAccessKeyId = ossAccessKeyId;
    }

    public String getOssAccessKeySecret() {
        return ossAccessKeySecret;
    }

    public void setOssAccessKeySecret(String ossAccessKeySecret) {
        this.ossAccessKeySecret = ossAccessKeySecret;
    }

    public String getOssKeyPrefix() {
        return ossKeyPrefix;
    }

    public void setOssKeyPrefix(String ossKeyPrefix) {
        this.ossKeyPrefix = ossKeyPrefix;
    }

    public String getOssPublicBaseUrl() {
        return ossPublicBaseUrl;
    }

    public void setOssPublicBaseUrl(String ossPublicBaseUrl) {
        this.ossPublicBaseUrl = ossPublicBaseUrl;
    }

    public boolean isOss() {
        return "oss".equalsIgnoreCase(type != null ? type.trim() : "local");
    }
}
