package com.attendance.dto.response;

import com.attendance.entity.ExportJob;
import java.time.LocalDateTime;

public class ExportJobDTO {
    private String id;
    private String exportType;
    private String status;
    private String fileName;
    private long rowCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private boolean downloadable;

    public static ExportJobDTO from(ExportJob job) {
        ExportJobDTO dto = new ExportJobDTO();
        dto.setId(job.getId());
        dto.setExportType(job.getExportType());
        dto.setStatus(job.getStatus());
        dto.setFileName(job.getFileName());
        dto.setRowCount(job.getRowCount());
        dto.setErrorMessage(job.getErrorMessage());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setCompletedAt(job.getCompletedAt());
        dto.setExpiresAt(job.getExpiresAt());
        dto.setDownloadable("completed".equals(job.getStatus())
                && job.getFilePath() != null
                && !job.getFilePath().isBlank());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExportType() {
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(long rowCount) {
        this.rowCount = rowCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public void setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
    }
}
