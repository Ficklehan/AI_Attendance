package com.attendance.dto.request;

public class DeleteTaskRequest {

    /** 已完成任务必填；待核对等状态可留空 */
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
