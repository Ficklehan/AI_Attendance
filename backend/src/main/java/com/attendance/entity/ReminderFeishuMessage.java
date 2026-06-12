package com.attendance.entity;

import java.time.LocalDateTime;

public class ReminderFeishuMessage {
    private String userId;
    private String ruleId;
    private String feishuMessageId;
    private LocalDateTime updatedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getFeishuMessageId() {
        return feishuMessageId;
    }

    public void setFeishuMessageId(String feishuMessageId) {
        this.feishuMessageId = feishuMessageId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
