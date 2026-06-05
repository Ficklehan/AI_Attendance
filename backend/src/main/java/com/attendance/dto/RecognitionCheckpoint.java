package com.attendance.dto;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 识别断点：支持多图从 imageIndex 恢复、记录已解析行数。
 */
public class RecognitionCheckpoint {

    private int imageIndex;
    private int pageIndex;
    private int recordCount;
    private int retryCount;
    private String lastError;

    public static RecognitionCheckpoint empty() {
        return new RecognitionCheckpoint();
    }

    public static RecognitionCheckpoint fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return empty();
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            RecognitionCheckpoint cp = new RecognitionCheckpoint();
            if (obj == null) {
                return empty();
            }
            cp.imageIndex = obj.getIntValue("imageIndex");
            cp.pageIndex = obj.getIntValue("pageIndex");
            cp.recordCount = obj.getIntValue("recordCount");
            cp.retryCount = obj.getIntValue("retryCount");
            cp.lastError = obj.getString("lastError");
            return cp;
        } catch (Exception e) {
            return empty();
        }
    }

    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("imageIndex", imageIndex);
        obj.put("pageIndex", pageIndex);
        obj.put("recordCount", recordCount);
        obj.put("retryCount", retryCount);
        if (lastError != null) {
            obj.put("lastError", lastError);
        }
        return obj.toJSONString();
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public void setImageIndex(int imageIndex) {
        this.imageIndex = imageIndex;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
