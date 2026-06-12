package com.attendance.dto;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内提醒消息渲染变量，用于按界面语言重新生成 title/body。
 */
public class NotificationContentVars {

    private int pendingCount;
    private String intervalValue;
    private String intervalUnit;
    private String taskStatus;
    private String latestTaskId;
    /** ISO-8601 local datetime, e.g. 2026-06-01T10:30:00 */
    private String latestTaskTime;
    private String recipientName;
    private String taskCreatorName;
    private List<String> creatorNames = new ArrayList<>();
    private boolean recipientIsOperator;

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public String getIntervalValue() {
        return intervalValue;
    }

    public void setIntervalValue(String intervalValue) {
        this.intervalValue = intervalValue;
    }

    public String getIntervalUnit() {
        return intervalUnit;
    }

    public void setIntervalUnit(String intervalUnit) {
        this.intervalUnit = intervalUnit;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getLatestTaskId() {
        return latestTaskId;
    }

    public void setLatestTaskId(String latestTaskId) {
        this.latestTaskId = latestTaskId;
    }

    public String getLatestTaskTime() {
        return latestTaskTime;
    }

    public void setLatestTaskTime(String latestTaskTime) {
        this.latestTaskTime = latestTaskTime;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getTaskCreatorName() {
        return taskCreatorName;
    }

    public void setTaskCreatorName(String taskCreatorName) {
        this.taskCreatorName = taskCreatorName;
    }

    public List<String> getCreatorNames() {
        return creatorNames;
    }

    public void setCreatorNames(List<String> creatorNames) {
        this.creatorNames = creatorNames;
    }

    public boolean isRecipientIsOperator() {
        return recipientIsOperator;
    }

    public void setRecipientIsOperator(boolean recipientIsOperator) {
        this.recipientIsOperator = recipientIsOperator;
    }

    public static NotificationContentVars fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(json, NotificationContentVars.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }
}
