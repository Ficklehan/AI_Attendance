package com.attendance.dto.request;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public class ConfirmTaskRequest {
    
    @NotEmpty(message = "数据不能为空")
    private List<Map<String, Object>> data;

    private List<String> imageUrls;

    private String anomalySummary;

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getAnomalySummary() {
        return anomalySummary;
    }

    public void setAnomalySummary(String anomalySummary) {
        this.anomalySummary = anomalySummary;
    }
}