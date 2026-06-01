package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

public class CalibrateRecordRequest {

    @NotBlank(message = "rowKey is required")
    private String rowKey;

    @NotNull(message = "updates is required")
    private Map<String, Object> updates;

    @NotBlank(message = "reason is required")
    private String reason;

    public String getRowKey() {
        return rowKey;
    }

    public void setRowKey(String rowKey) {
        this.rowKey = rowKey;
    }

    public Map<String, Object> getUpdates() {
        return updates;
    }

    public void setUpdates(Map<String, Object> updates) {
        this.updates = updates;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
