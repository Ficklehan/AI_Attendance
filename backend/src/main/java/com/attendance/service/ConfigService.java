package com.attendance.service;

import com.attendance.config.CountryCatalog;
import com.attendance.dto.CountryConfigBundle;
import com.attendance.mapper.PluginConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.attendance.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    static final String GLOBAL_WORKING_COUNTRY_KEY = "current_working_country";

    @Autowired
    private MarkdownConfigService markdownConfigService;

    @Autowired
    private FeishuCountryConfigService feishuCountryConfigService;

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    @PostConstruct
    public void loadGlobalWorkingCountry() {
        try {
            String stored = pluginConfigMapper.selectValue(GLOBAL_WORKING_COUNTRY_KEY);
            if (stored != null && !stored.trim().isEmpty()) {
                String normalized = stored.trim();
                if ("DE".equalsIgnoreCase(normalized)) {
                    log.warn("全局工作国家曾为 DE，已按规范迁移为 default（法国 FR）");
                    normalized = "default";
                    pluginConfigMapper.upsertValue(
                            GLOBAL_WORKING_COUNTRY_KEY,
                            normalized,
                            "string",
                            "全局工作国家（default 表示法国）");
                }
                markdownConfigService.setCountry(normalized);
                log.info("已加载全局工作国家配置: {}", normalized);
            } else {
                markdownConfigService.setCountry("default");
            }
        } catch (Exception e) {
            log.warn("加载全局工作国家配置失败，使用 default: {}", e.getMessage());
            markdownConfigService.setCountry("default");
        }
    }
    
    public String getConfigValue(String configKey, String defaultValue) {
        Map<String, String> allConfigs = getAllConfigs();
        return allConfigs.getOrDefault(configKey, defaultValue);
    }
    
    public Map<String, String> getAllConfigs() {
        Map<String, String> configs = new HashMap<>();
        configs.put("current_country", markdownConfigService.getCurrentCountry());
        return configs;
    }

    public Map<String, String> getAllConfigs(String country) {
        Map<String, String> configs = new HashMap<>();
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
        try {
            String userId = SecurityUtils.getCurrentUserId();
            if (userId != null && !userId.trim().isEmpty()) {
                return userService.resolveWorkingCountryForUserId(userId);
            }
        } catch (Exception ignored) {
            // 未登录或安全上下文不可用
        }
        return getGlobalWorkingCountry();
    }

    public String getGlobalWorkingCountry() {
        return CountryCatalog.resolveGlobalDefaultCountry(markdownConfigService.getCurrentCountry());
    }
    
    public void setCurrentCountry(String country) {
        String stored = country == null || country.trim().isEmpty() ? "default" : country.trim();
        markdownConfigService.setCountry(stored);
        try {
            pluginConfigMapper.upsertValue(
                    GLOBAL_WORKING_COUNTRY_KEY,
                    stored,
                    "string",
                    "全局工作国家（default 表示法国）");
        } catch (Exception e) {
            log.error("持久化全局工作国家失败: country={}", stored, e);
        }
    }
    
    public List<String> getAllCountries() {
        return markdownConfigService.getAllCountries();
    }
    
    public void updateFeishuConfig(String country, String appToken, String tableId, String fieldMappingYaml) {
        try {
            markdownConfigService.updateFeishuConfig(country, appToken, tableId, fieldMappingYaml);
            log.info("飞书配置已更新: country={}", country);
        } catch (Exception e) {
            log.error("更新飞书配置失败", e);
            throw new RuntimeException("更新飞书配置失败: " + e.getMessage());
        }
    }

    public void updateFeishuConfig(String country,
                                 String appToken,
                                 String tableId,
                                 List<Map<String, Object>> fieldMapping,
                                 Boolean syncEnabled) {
        markdownConfigService.updateFeishuConfig(country, appToken, tableId, fieldMapping, syncEnabled);
    }
    
    public void deleteConfig(String configKey) {
        throw new RuntimeException("Markdown配置文件不支持删除操作");
    }
    
    public String resolveEffectiveCountry(String country) {
        return markdownConfigService.resolveEffectiveCountry(country);
    }

    public String resolveEffectiveFeishuCountry(String country) {
        return markdownConfigService.resolveEffectiveFeishuCountry(country);
    }

    public boolean isFeishuSyncEnabled(String country) {
        return feishuCountryConfigService.isSyncEnabled(country);
    }

    public CountryConfigBundle getCountryConfigBundle(String country) {
        return markdownConfigService.getCountryConfigBundle(country);
    }
}
