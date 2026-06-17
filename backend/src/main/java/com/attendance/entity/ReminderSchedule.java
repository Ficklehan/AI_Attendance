package com.attendance.entity;

import java.time.LocalDateTime;

public class ReminderSchedule {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_CANCELLED = "cancelled";

    private String id;
    private String ruleId;
    private String taskId;
    private String userId;
    private long periodIndex;
    private String periodBucket;
    private LocalDateTime dueAt;
    private LocalDateTime statusEnteredAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

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

    public long getPeriodIndex() {
        return periodIndex;
    }

    public void setPeriodIndex(long periodIndex) {
        this.periodIndex = periodIndex;
    }

    public String getPeriodBucket() {
        return periodBucket;
    }

    public void setPeriodBucket(String periodBucket) {
        this.periodBucket = periodBucket;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public LocalDateTime getStatusEnteredAt() {
        return statusEnteredAt;
    }

    public void setStatusEnteredAt(LocalDateTime statusEnteredAt) {
        this.statusEnteredAt = statusEnteredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
