package com.attendance.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ReminderRule {
    private String id;
    private String name;
    private String description;
    private String taskStatusesJson;
    private String scopeCountriesJson;
    private String scopeRolesJson;
    private BigDecimal intervalValue;
    private String intervalUnit;
    private String messageTemplate;
    private String messageTemplateSupervisor;
    private boolean includeTaskCreator;
    private boolean enabled;
    private LocalDateTime lastRunAt;
    private int lastHitCount;
    private int lastSentCount;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 非数据库字段 */
    private List<String> taskStatuses;
    private List<String> scopeCountries;
    private List<String> scopeRoles;
    private List<String> recipientUserIds;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskStatusesJson() {
        return taskStatusesJson;
    }

    public void setTaskStatusesJson(String taskStatusesJson) {
        this.taskStatusesJson = taskStatusesJson;
    }

    public String getScopeCountriesJson() {
        return scopeCountriesJson;
    }

    public void setScopeCountriesJson(String scopeCountriesJson) {
        this.scopeCountriesJson = scopeCountriesJson;
    }

    public String getScopeRolesJson() {
        return scopeRolesJson;
    }

    public void setScopeRolesJson(String scopeRolesJson) {
        this.scopeRolesJson = scopeRolesJson;
    }

    public BigDecimal getIntervalValue() {
        return intervalValue;
    }

    public void setIntervalValue(BigDecimal intervalValue) {
        this.intervalValue = intervalValue;
    }

    public String getIntervalUnit() {
        return intervalUnit;
    }

    public void setIntervalUnit(String intervalUnit) {
        this.intervalUnit = intervalUnit;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public String getMessageTemplateSupervisor() {
        return messageTemplateSupervisor;
    }

    public void setMessageTemplateSupervisor(String messageTemplateSupervisor) {
        this.messageTemplateSupervisor = messageTemplateSupervisor;
    }

    public boolean isIncludeTaskCreator() {
        return includeTaskCreator;
    }

    public void setIncludeTaskCreator(boolean includeTaskCreator) {
        this.includeTaskCreator = includeTaskCreator;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public int getLastHitCount() {
        return lastHitCount;
    }

    public void setLastHitCount(int lastHitCount) {
        this.lastHitCount = lastHitCount;
    }

    public int getLastSentCount() {
        return lastSentCount;
    }

    public void setLastSentCount(int lastSentCount) {
        this.lastSentCount = lastSentCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getTaskStatuses() {
        return taskStatuses;
    }

    public void setTaskStatuses(List<String> taskStatuses) {
        this.taskStatuses = taskStatuses;
    }

    public List<String> getScopeCountries() {
        return scopeCountries;
    }

    public void setScopeCountries(List<String> scopeCountries) {
        this.scopeCountries = scopeCountries;
    }

    public List<String> getScopeRoles() {
        return scopeRoles;
    }

    public void setScopeRoles(List<String> scopeRoles) {
        this.scopeRoles = scopeRoles;
    }

    public List<String> getRecipientUserIds() {
        return recipientUserIds;
    }

    public void setRecipientUserIds(List<String> recipientUserIds) {
        this.recipientUserIds = recipientUserIds;
    }
}
