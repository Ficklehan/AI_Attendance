package com.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;

public class ConfigUpdateRequest {
    
    @NotBlank(message = "配置key不能为空")
    private String configKey;
    
    @NotBlank(message = "配置值不能为空")
    private String configValue;
    
    private String configType = "string";
    
    private String description;

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}