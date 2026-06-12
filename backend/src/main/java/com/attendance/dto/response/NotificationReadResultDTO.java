package com.attendance.dto.response;

public class NotificationReadResultDTO {
    private boolean read;
    private boolean removed;
    private boolean taskDeleted;

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public boolean isTaskDeleted() {
        return taskDeleted;
    }

    public void setTaskDeleted(boolean taskDeleted) {
        this.taskDeleted = taskDeleted;
    }
}
