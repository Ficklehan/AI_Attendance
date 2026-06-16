package com.attendance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ReminderRuleDTO {
    private String id;
    private String name;
    private String description;
    private List<String> taskStatuses;
    private List<String> scopeCountries;
    private List<String> scopeRoles;
    private BigDecimal intervalValue;
    private String intervalUnit;
    private Integer scheduleHourOfDay;
    private String messageTemplate;
    private String messageTemplateSupervisor;
    private Map<String, String> messageTemplateLocales;
    private Map<String, String> messageTemplateSupervisorLocales;
    private boolean includeTaskCreator;
    private boolean enabled;
    private List<String> recipientUserIds;
    private int recipientCount;
    private LocalDateTime lastRunAt;
    private int lastHitCount;
    private int lastSentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Integer getScheduleHourOfDay() {
        return scheduleHourOfDay;
    }

    public void setScheduleHourOfDay(Integer scheduleHourOfDay) {
        this.scheduleHourOfDay = scheduleHourOfDay;
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

    public Map<String, String> getMessageTemplateLocales() {
        return messageTemplateLocales;
    }

    public void setMessageTemplateLocales(Map<String, String> messageTemplateLocales) {
        this.messageTemplateLocales = messageTemplateLocales;
    }

    public Map<String, String> getMessageTemplateSupervisorLocales() {
        return messageTemplateSupervisorLocales;
    }

    public void setMessageTemplateSupervisorLocales(Map<String, String> messageTemplateSupervisorLocales) {
        this.messageTemplateSupervisorLocales = messageTemplateSupervisorLocales;
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

    public List<String> getRecipientUserIds() {
        return recipientUserIds;
    }

    public void setRecipientUserIds(List<String> recipientUserIds) {
        this.recipientUserIds = recipientUserIds;
    }

    public int getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(int recipientCount) {
        this.recipientCount = recipientCount;
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
}
