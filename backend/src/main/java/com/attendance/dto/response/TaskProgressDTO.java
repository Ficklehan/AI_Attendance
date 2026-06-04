package com.attendance.dto.response;

/**
 * 任务进度轻量视图（识别轮询专用，不含 raw_data / confirmed_data）。
 */
public class TaskProgressDTO {
    private String taskId;
    private String status;
    private String syncStatus;
    private String syncError;
    private String aiRawOutput;
    private int progressRowCount;
    private String progressError;
    /** MyBatis 填充，不返回给前端 */
    private String anomalySummaryRaw;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getSyncError() {
        return syncError;
    }

    public void setSyncError(String syncError) {
        this.syncError = syncError;
    }

    public String getAiRawOutput() {
        return aiRawOutput;
    }

    public void setAiRawOutput(String aiRawOutput) {
        this.aiRawOutput = aiRawOutput;
    }

    public int getProgressRowCount() {
        return progressRowCount;
    }

    public void setProgressRowCount(int progressRowCount) {
        this.progressRowCount = progressRowCount;
    }

    public String getProgressError() {
        return progressError;
    }

    public void setProgressError(String progressError) {
        this.progressError = progressError;
    }

    public String getAnomalySummaryRaw() {
        return anomalySummaryRaw;
    }

    public void setAnomalySummaryRaw(String anomalySummaryRaw) {
        this.anomalySummaryRaw = anomalySummaryRaw;
    }
}
