package com.attendance.util;

import com.attendance.config.FeishuProperties;
import com.attendance.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeishuConfigManager {
    
    private static final Logger log = LoggerFactory.getLogger(FeishuConfigManager.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private FeishuProperties feishuProperties;

    private final Map<String, CountryConfig> configCache = new ConcurrentHashMap<>();

    public static class CountryConfig {
        private String bitableAppToken;
        private String bitableTableId;
        private Map<String, FieldMapping> fieldMappings;

        public String getBitableAppToken() {
            return bitableAppToken;
        }

        public void setBitableAppToken(String bitableAppToken) {
            this.bitableAppToken = bitableAppToken;
        }

        public String getBitableTableId() {
            return bitableTableId;
        }

        public void setBitableTableId(String bitableTableId) {
            this.bitableTableId = bitableTableId;
        }

        public Map<String, FieldMapping> getFieldMappings() {
            return fieldMappings;
        }

        public void setFieldMappings(Map<String, FieldMapping> fieldMappings) {
            this.fieldMappings = fieldMappings;
        }
    }

    public static class FieldMapping {
        private String aiField;
        private String feishuField;
        private String type;
        private boolean required;

        public FieldMapping(String aiField, String feishuField, String type, boolean required) {
            this.aiField = aiField;
            this.feishuField = feishuField;
            this.type = type;
            this.required = required;
        }

        public String getAiField() {
            return aiField;
        }

        public String getFeishuField() {
            return feishuField;
        }

        public String getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }
    }

    public CountryConfig getConfig(String countryCode) {
        String effectiveCode = countryCode != null ? countryCode.toUpperCase() : "DEFAULT";
        
        if (configCache.containsKey(effectiveCode)) {
            return configCache.get(effectiveCode);
        }

        CountryConfig config = loadConfig(effectiveCode);
        configCache.put(effectiveCode, config);
        
        return config;
    }

    private CountryConfig loadConfig(String countryCode) {
        CountryConfig config = new CountryConfig();

        String appTokenKey = "feishu_bitable_app_token_" + countryCode;
        String tableIdKey = "feishu_bitable_table_id_" + countryCode;
        String fieldMappingKey = "feishu_field_mapping_" + countryCode;

        config.setBitableAppToken(configService.getConfigValue(appTokenKey, feishuProperties.getAppId()));
        config.setBitableTableId(configService.getConfigValue(tableIdKey, ""));

        String mappingJson = configService.getConfigValue(fieldMappingKey, null);
        if (mappingJson != null) {
            config.setFieldMappings(parseFieldMappings(mappingJson));
        } else {
            config.setFieldMappings(getDefaultFieldMappings());
        }

        return config;
    }

    private Map<String, FieldMapping> parseFieldMappings(String json) {
        Map<String, FieldMapping> mappings = new ConcurrentHashMap<>();
        try {
            com.alibaba.fastjson.JSONArray array = com.alibaba.fastjson.JSON.parseArray(json);
            for (int i = 0; i < array.size(); i++) {
                com.alibaba.fastjson.JSONObject obj = array.getJSONObject(i);
                String aiField = obj.getString("aiField");
                String feishuField = obj.getString("feishuField");
                String type = obj.getString("type");
                boolean required = obj.getBooleanValue("required");
                
                mappings.put(aiField, new FieldMapping(aiField, feishuField, type, required));
            }
        } catch (Exception e) {
            log.error("解析字段映射失败", e);
        }
        return mappings;
    }

    private Map<String, FieldMapping> getDefaultFieldMappings() {
        Map<String, FieldMapping> mappings = new ConcurrentHashMap<>();
        
        mappings.put("NO", new FieldMapping("NO", "NO", "string", true));
        mappings.put("NOM_PRENOM", new FieldMapping("NOM_PRENOM", "NOM PRENOM", "string", false));
        mappings.put("AGENCE_INTERIMAIRE", new FieldMapping("AGENCE_INTERIMAIRE", "AGENCE D'INTERIMAIR", "string", false));
        mappings.put("HORAIRES_DU_TRAVAIL", new FieldMapping("HORAIRES_DU_TRAVAIL", "HORAIRES DU TRAVAI", "string", false));
        mappings.put("Date", new FieldMapping("Date", "Date", "date", true));
        mappings.put("ARRIVEE_DATETIME", new FieldMapping("ARRIVEE_DATETIME", "ARRIVE", "datetime", true));
        mappings.put("DEPAR_DATETIME", new FieldMapping("DEPAR_DATETIME", "DEPAR", "datetime", true));
        mappings.put("PAUSE", new FieldMapping("PAUSE", "PAUS", "number", true));
        mappings.put("CHECKER", new FieldMapping("CHECKER", "CHECKER", "string", false));
        mappings.put("SmartMark", new FieldMapping("SmartMark", "Mark", "string", false));
        
        return mappings;
    }

    public void clearCache() {
        configCache.clear();
        log.info("飞书配置缓存已清空");
    }

    public void clearCache(String countryCode) {
        if (countryCode != null) {
            configCache.remove(countryCode.toUpperCase());
        }
    }
}