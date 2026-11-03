package com.attendance.dto;

import java.util.List;
import java.util.Map;

/**
 * 按国家解析后的配置：飞书多维表可回退到全局 default。
 */
public class CountryConfigBundle {

    private String requestCountry;
    private String effectiveFeishuCountry;
    private String appToken;
    private String tableId;
    private List<Map<String, Object>> fieldMapping;
    private boolean feishuFromGlobalFallback;
    private boolean syncEnabled = true;

    public String getRequestCountry() {
        return requestCountry;
    }

    public void setRequestCountry(String requestCountry) {
        this.requestCountry = requestCountry;
    }

    public String getEffectiveFeishuCountry() {
        return effectiveFeishuCountry;
    }

    public void setEffectiveFeishuCountry(String effectiveFeishuCountry) {
        this.effectiveFeishuCountry = effectiveFeishuCountry;
    }

    public String getAppToken() {
        return appToken;
    }

    public void setAppToken(String appToken) {
        this.appToken = appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public List<Map<String, Object>> getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(List<Map<String, Object>> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public boolean isFeishuFromGlobalFallback() {
        return feishuFromGlobalFallback;
    }

    public void setFeishuFromGlobalFallback(boolean feishuFromGlobalFallback) {
        this.feishuFromGlobalFallback = feishuFromGlobalFallback;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    /** Safe summary for non-admin clients: no tokens, prompts, or field mappings. */
    public CountryConfigBundle toPublicSummary() {
        CountryConfigBundle summary = new CountryConfigBundle();
        summary.setRequestCountry(requestCountry);
        summary.setEffectiveFeishuCountry(effectiveFeishuCountry);
        summary.setFeishuFromGlobalFallback(feishuFromGlobalFallback);
        summary.setSyncEnabled(syncEnabled);
        return summary;
    }
}
