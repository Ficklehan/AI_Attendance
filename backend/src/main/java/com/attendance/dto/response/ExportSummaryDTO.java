package com.attendance.dto.response;

public class ExportSummaryDTO {
    private long activeCount;

    public ExportSummaryDTO() {
    }

    public ExportSummaryDTO(long activeCount) {
        this.activeCount = activeCount;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(long activeCount) {
        this.activeCount = activeCount;
    }
}
