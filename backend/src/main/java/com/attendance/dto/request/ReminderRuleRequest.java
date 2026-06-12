package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ReminderRuleRequest {

    @NotBlank
    private String name;

    private String description;

    @NotEmpty
    private List<String> taskStatuses;

    /** 适用工作国家（tasks.prompt_country）；空或不传表示全部 */
    private List<String> scopeCountries;

    /** 适用任务创建者角色；空或不传表示全部 */
    private List<String> scopeRoles;

    @NotNull
    private BigDecimal intervalValue;

    @NotBlank
    private String intervalUnit;

    /** 兼容字段：主语言（zh-CN）操作者文案 */
    private String messageTemplate;

    /** 非任务操作者（督办人）文案；空则使用系统默认督办模板 */
    private String messageTemplateSupervisor;

    /** locale -> 操作者文案，如 zh-CN / fr-FR */
    private Map<String, String> messageTemplateLocales;

    /** locale -> 督办人文案 */
    private Map<String, String> messageTemplateSupervisorLocales;

    @NotNull
    private Boolean includeTaskCreator;

    @NotNull
    private Boolean enabled;

    @NotEmpty
    private List<String> recipientUserIds;

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

    public Boolean getIncludeTaskCreator() {
        return includeTaskCreator;
    }

    public void setIncludeTaskCreator(Boolean includeTaskCreator) {
        this.includeTaskCreator = includeTaskCreator;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRecipientUserIds() {
        return recipientUserIds;
    }

    public void setRecipientUserIds(List<String> recipientUserIds) {
        this.recipientUserIds = recipientUserIds;
    }
}
