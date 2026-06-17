package com.attendance.dto;

import java.util.List;
import java.util.Map;

/**
 * 按国家解析后的配置：提示词与飞书多维表可分别回退到全局 default。
 */
public class CountryConfigBundle {

    private String requestCountry;
    private String effectivePromptCountry;
    private String effectiveFeishuCountry;
    private String promptSection;
    private String aiPrompt;
    private String continuePrompt;
    private String appToken;
    private String tableId;
    private List<Map<String, Object>> fieldMapping;
    private boolean promptFromGlobalFallback;
    private boolean feishuFromGlobalFallback;
    private boolean syncEnabled = true;

    public String getRequestCountry() {
        return requestCountry;
    }

    public void setRequestCountry(String requestCountry) {
        this.requestCountry = requestCountry;
    }

    public String getEffectivePromptCountry() {
        return effectivePromptCountry;
    }

    public void setEffectivePromptCountry(String effectivePromptCountry) {
        this.effectivePromptCountry = effectivePromptCountry;
    }

    public String getEffectiveFeishuCountry() {
        return effectiveFeishuCountry;
    }

    public void setEffectiveFeishuCountry(String effectiveFeishuCountry) {
        this.effectiveFeishuCountry = effectiveFeishuCountry;
    }

    public String getPromptSection() {
        return promptSection;
    }

    public void setPromptSection(String promptSection) {
        this.promptSection = promptSection;
    }

    public String getAiPrompt() {
        return aiPrompt;
    }

    public void setAiPrompt(String aiPrompt) {
        this.aiPrompt = aiPrompt;
    }

    public String getContinuePrompt() {
        return continuePrompt;
    }

    public void setContinuePrompt(String continuePrompt) {
        this.continuePrompt = continuePrompt;
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

    public boolean isPromptFromGlobalFallback() {
        return promptFromGlobalFallback;
    }

    public void setPromptFromGlobalFallback(boolean promptFromGlobalFallback) {
        this.promptFromGlobalFallback = promptFromGlobalFallback;
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
        summary.setEffectivePromptCountry(effectivePromptCountry);
        summary.setEffectiveFeishuCountry(effectiveFeishuCountry);
        summary.setPromptSection(promptSection);
        summary.setPromptFromGlobalFallback(promptFromGlobalFallback);
        summary.setFeishuFromGlobalFallback(feishuFromGlobalFallback);
        summary.setSyncEnabled(syncEnabled);
        return summary;
    }
}
