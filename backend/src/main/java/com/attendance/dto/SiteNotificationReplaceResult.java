package com.attendance.dto;

/**
 * Result of replacing a merged site notification for the same rule/period bucket.
 */
public class SiteNotificationReplaceResult {

    private final String notificationId;
    private final String previousFeishuMessageId;

    public SiteNotificationReplaceResult(String notificationId, String previousFeishuMessageId) {
        this.notificationId = notificationId;
        this.previousFeishuMessageId = previousFeishuMessageId;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getPreviousFeishuMessageId() {
        return previousFeishuMessageId;
    }
}
