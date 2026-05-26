package com.attendance.entity;

import java.time.LocalDateTime;

public class Task {
    private String taskId;
    private String userId;
    private String fileKey;
    private String status;
    private String rawData;
    private String confirmedData;
    private String imageUrls;
    private String anomalySummary;
    private String aiRawOutput;
    private String processedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getConfirmedData() {
        return confirmedData;
    }

    public void setConfirmedData(String confirmedData) {
        this.confirmedData = confirmedData;
    }

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getAnomalySummary() {
        return anomalySummary;
    }

    public void setAnomalySummary(String anomalySummary) {
        this.anomalySummary = anomalySummary;
    }

    public String getAiRawOutput() {
        return aiRawOutput;
    }

    public void setAiRawOutput(String aiRawOutput) {
        this.aiRawOutput = aiRawOutput;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
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