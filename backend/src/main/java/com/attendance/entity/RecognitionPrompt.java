package com.attendance.entity;

import java.time.LocalDateTime;

/**
 * 按国家存储的 AI 识别提示词（数据库为运行时唯一数据源）。
 */
public class RecognitionPrompt {

    private String countryCode;
    private String aiPrompt;
    private String continuePrompt;
    private int seedVersion;
    private boolean userModified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getAiPrompt() {
        return aiPrompt;
    }

    public void setAiPrompt(String aiPrompt) {
        this.aiPrompt = aiPrompt;
    }

    public String getContinuePrompt() {
        return continuePrompt;
    }

    public void setContinuePrompt(String continuePrompt) {
        this.continuePrompt = continuePrompt;
    }

    public int getSeedVersion() {
        return seedVersion;
    }

    public void setSeedVersion(int seedVersion) {
        this.seedVersion = seedVersion;
    }

    public boolean isUserModified() {
        return userModified;
    }

    public void setUserModified(boolean userModified) {
        this.userModified = userModified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
