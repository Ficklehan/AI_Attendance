package com.attendance.dto.response;

import com.attendance.entity.Task;
import java.time.LocalDateTime;

public class TaskListDTO {
    private String taskId;
    private String fileKey;
    private String status;
    private String imageUrls;
    private String userName;
    private LocalDateTime createdAt;

    public TaskListDTO() {}

    public TaskListDTO(Task task, String userName) {
        this.taskId = task.getTaskId();
        this.fileKey = task.getFileKey();
        this.status = task.getStatus();
        this.imageUrls = task.getImageUrls();
        this.userName = userName;
        this.createdAt = task.getCreatedAt();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}