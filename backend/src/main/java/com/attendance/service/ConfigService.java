package com.attendance.service;

import com.attendance.dto.CountryConfigBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    @Autowired
    private MarkdownConfigService markdownConfigService;
    
    public String getConfigValue(String configKey, String defaultValue) {
        Map<String, String> allConfigs = getAllConfigs();
        return allConfigs.getOrDefault(configKey, defaultValue);
    }
    
    public Map<String, String> getAllConfigs() {
        Map<String, String> configs = new HashMap<>();
        configs.put("ai_prompt", markdownConfigService.getAiPrompt());
        configs.put("continue_prompt", markdownConfigService.getContinuePrompt());
        configs.put("current_country", markdownConfigService.getCurrentCountry());
        return configs;
    }
    
    public Map<String, String> getAllConfigs(String country) {
        Map<String, String> configs = new HashMap<>();
        configs.put("ai_prompt", markdownConfigService.getAiPrompt(country));
        configs.put("continue_prompt", markdownConfigService.getContinuePrompt(country));
        configs.put("country", country);
        return configs;
    }
    
    public Map<String, Object> getFeishuConfig() {
        return markdownConfigService.getFeishuConfig();
    }
    
    public Map<String, Object> getFeishuConfig(String country) {
        return markdownConfigService.getFeishuConfig(country);
    }
    
    public List<Map<String, Object>> getFieldMapping() {
        return markdownConfigService.getFieldMapping();
    }
    
    public List<Map<String, Object>> getFieldMapping(String country) {
        return markdownConfigService.getFieldMapping(country);
    }
    
    public String getCurrentCountry() {
        return markdownConfigService.getCurrentCountry();
    }
    
    public void setCurrentCountry(String country) {
        markdownConfigService.setCountry(country);
    }
    
    public List<String> getAllCountries() {
        return markdownConfigService.getAllCountries();
    }
    
    public void updateConfig(String configKey, String configValue) {
        try {
            if ("ai_prompt".equals(configKey) || "continue_prompt".equals(configKey)) {
                String aiPrompt = "ai_prompt".equals(configKey) ? configValue : markdownConfigService.getAiPrompt();
                String continuePrompt = "continue_prompt".equals(configKey) ? configValue : markdownConfigService.getContinuePrompt();
                markdownConfigService.updatePrompt(aiPrompt, continuePrompt);
            } else {
                throw new RuntimeException("不支持的配置键: " + configKey);
            }
            log.info("更新配置: key={}", configKey);
        } catch (IOException e) {
            log.error("更新配置文件失败", e);
            throw new RuntimeException("更新配置失败: " + e.getMessage());
        }
    }
    
    public void updateConfig(String country, String configKey, String configValue) {
        try {
            if ("ai_prompt".equals(configKey) || "continue_prompt".equals(configKey)) {
                String aiPrompt = "ai_prompt".equals(configKey) ? configValue : markdownConfigService.getAiPrompt(country);
                String continuePrompt = "continue_prompt".equals(configKey) ? configValue : markdownConfigService.getContinuePrompt(country);
                markdownConfigService.updatePrompt(country, aiPrompt, continuePrompt);
            } else {
                throw new RuntimeException("不支持的配置键: " + configKey);
            }
            log.info("更新配置: country={}, key={}", country, configKey);
        } catch (IOException e) {
            log.error("更新配置文件失败", e);
            throw new RuntimeException("更新配置失败: " + e.getMessage());
        }
    }
    
    public void updateFeishuConfig(String country, String appToken, String tableId, String fieldMappingYaml) {
        try {
            markdownConfigService.updateFeishuConfig(country, appToken, tableId, fieldMappingYaml);
            log.info("飞书配置已更新: country={}", country);
        } catch (IOException e) {
            log.error("更新飞书配置失败", e);
            throw new RuntimeException("更新飞书配置失败: " + e.getMessage());
        }
    }
    
    public void deleteConfig(String configKey) {
        throw new RuntimeException("Markdown配置文件不支持删除操作");
    }
    
    public String getAiPrompt() {
        return markdownConfigService.getAiPrompt();
    }
    
    public String getAiPrompt(String country) {
        return markdownConfigService.getAiPrompt(country);
    }
    
    public String getContinuePrompt() {
        return markdownConfigService.getContinuePrompt();
    }
    
    public String getContinuePrompt(String country) {
        return markdownConfigService.getContinuePrompt(country);
    }

    public String resolveEffectiveCountry(String country) {
        return markdownConfigService.resolveEffectiveCountry(country);
    }

    public String describePromptSection(String country) {
        return markdownConfigService.describePromptSection(country);
    }

    public String resolveEffectivePromptCountry(String country) {
        return markdownConfigService.resolveEffectivePromptCountry(country);
    }

    public String resolveEffectiveFeishuCountry(String country) {
        return markdownConfigService.resolveEffectiveFeishuCountry(country);
    }

    public CountryConfigBundle getCountryConfigBundle(String country) {
        return markdownConfigService.getCountryConfigBundle(country);
    }
}
