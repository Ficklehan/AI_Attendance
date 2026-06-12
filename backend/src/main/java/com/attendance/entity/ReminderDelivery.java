package com.attendance.entity;

import java.time.LocalDateTime;

public class ReminderDelivery {
    private String id;
    private String ruleId;
    private String taskId;
    private String userId;
    private String periodBucket;
    private boolean channelSite;
    private boolean channelFeishu;
    private String feishuStatus;
    private LocalDateTime createdAt;

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

    public String getPeriodBucket() {
        return periodBucket;
    }

    public void setPeriodBucket(String periodBucket) {
        this.periodBucket = periodBucket;
    }

    public boolean isChannelSite() {
        return channelSite;
    }

    public void setChannelSite(boolean channelSite) {
        this.channelSite = channelSite;
    }

    public boolean isChannelFeishu() {
        return channelFeishu;
    }

    public void setChannelFeishu(boolean channelFeishu) {
        this.channelFeishu = channelFeishu;
    }

    public String getFeishuStatus() {
        return feishuStatus;
    }

    public void setFeishuStatus(String feishuStatus) {
        this.feishuStatus = feishuStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
