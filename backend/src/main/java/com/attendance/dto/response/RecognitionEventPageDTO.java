package com.attendance.dto.response;

import java.util.ArrayList;
import java.util.List;

public class RecognitionEventPageDTO {
    private String taskId;
    private String status;
    private int serverSeq;
    private List<RecognitionEventDTO> events = new ArrayList<>();
    private boolean hasMore;

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

    public int getServerSeq() {
        return serverSeq;
    }

    public void setServerSeq(int serverSeq) {
        this.serverSeq = serverSeq;
    }

    public List<RecognitionEventDTO> getEvents() {
        return events;
    }

    public void setEvents(List<RecognitionEventDTO> events) {
        this.events = events != null ? events : new ArrayList<>();
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
