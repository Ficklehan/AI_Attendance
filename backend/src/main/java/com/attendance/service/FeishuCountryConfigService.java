package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.attendance.config.PromptProperties;
import com.attendance.entity.FeishuCountryConfig;
import com.attendance.mapper.FeishuCountryConfigMapper;
import com.attendance.util.FeishuCanonicalParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeishuCountryConfigService {

    private static final Logger log = LoggerFactory.getLogger(FeishuCountryConfigService.class);

    private final ConcurrentHashMap<String, Map<String, Object>> configCache = new ConcurrentHashMap<>();

    @Autowired
    private FeishuCountryConfigMapper feishuCountryConfigMapper;

    @Autowired
    private PromptProperties promptProperties;

    public Map<String, Object> getFeishuConfig(String country) {
        String request = normalizeCountry(country);
        String effective = resolveEffectiveFeishuCountry(request);
        return configCache.computeIfAbsent(effective, this::loadConfigMapFromDb);
    }

    /**
     * 按请求国家读取字段映射：优先该国数据库行，无配置时再回退 default。
     */
    public List<Map<String, Object>> getFieldMapping(String country) {
        String normalized = normalizeCountry(country);
        List<Map<String, Object>> own = loadFieldMappingFromRow(normalized);
        if (!own.isEmpty()) {
            log.debug("字段映射命中国家行: country={}, items={}", normalized, own.size());
            return own;
        }
        if (!"default".equalsIgnoreCase(normalized)) {
            String effective = resolveEffectiveFeishuCountry(normalized);
            if (!normalized.equalsIgnoreCase(effective)) {
                List<Map<String, Object>> fallback = loadFieldMappingFromRow(effective);
                if (!fallback.isEmpty()) {
                    log.info("国家 {} 无字段映射，回退使用 {}", normalized, effective);
                    return fallback;
                }
            }
        }
        return own;
    }

    /** 推送多维表时应使用的映射国家（与连接配置回退规则一致，但单独记录便于排查）。 */
    public String resolveFieldMappingCountry(String country) {
        String normalized = normalizeCountry(country);
        if (!loadFieldMappingFromRow(normalized).isEmpty()) {
            return normalized;
        }
        return resolveEffectiveFeishuCountry(normalized);
    }

    private List<Map<String, Object>> loadFieldMappingFromRow(String countryCode) {
        FeishuCountryConfig row = feishuCountryConfigMapper.selectByCountry(countryCode);
        if (row == null) {
            return new ArrayList<>();
        }
        return parseFieldMappingJson(row.getFieldMappingJson());
    }

    public boolean isSyncEnabled(String country) {
        Object value = getFeishuConfig(country).get("syncEnabled");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return true;
    }

    public String resolveEffectiveFeishuCountry(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return "default";
        }
        String normalized = country.trim().toUpperCase();
        if (hasCountryRow(normalized)) {
            return normalized;
        }
        log.debug("国家 {} 无独立飞书配置行，回退 default", normalized);
        return "default";
    }

    public boolean hasCountryRow(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return true;
        }
        return feishuCountryConfigMapper.selectByCountry(country.trim().toUpperCase()) != null;
    }

    public void saveUserConfig(String country,
                               String appToken,
                               String tableId,
                               List<Map<String, Object>> fieldMapping,
                               Boolean syncEnabled) {
        FeishuCountryConfig existing = feishuCountryConfigMapper.selectByCountry(normalizeCountry(country));
        FeishuCountryConfig row = new FeishuCountryConfig();
        row.setCountryCode(normalizeCountry(country));
        row.setAppToken(appToken != null ? appToken.trim() : "");
        row.setTableId(tableId != null ? tableId.trim() : "");
        row.setFieldMappingJson(JSON.toJSONString(fieldMapping != null ? fieldMapping : new ArrayList<>()));
        if (syncEnabled != null) {
            row.setSyncEnabled(syncEnabled);
        } else if (existing != null) {
            row.setSyncEnabled(existing.isSyncEnabled());
        } else {
            row.setSyncEnabled(true);
        }
        row.setSeedVersion(promptProperties.getSeedVersion());
        row.setUserModified(true);
        feishuCountryConfigMapper.upsertUserEdit(row);
        clearCache();
        log.info("飞书配置已保存到数据库: country={}, syncEnabled={}", row.getCountryCode(), row.isSyncEnabled());
    }

    public void saveFieldMapping(String country, List<Map<String, Object>> fieldMapping) {
        Map<String, Object> current = getFeishuConfig(country);
        saveUserConfig(
                country,
                stringValue(current.get("appToken")),
                stringValue(current.get("tableId")),
                fieldMapping,
                boolValue(current.get("syncEnabled")));
    }

    public long countRows() {
        return feishuCountryConfigMapper.countAll();
    }

    public List<String> listCountryCodes() {
        return feishuCountryConfigMapper.selectAllCountryCodes();
    }

    public int seedFromCanonical(boolean force) {
        String markdown = readCanonicalResource();
        if (markdown == null || markdown.trim().isEmpty()) {
            log.error("内置 canonical feishu.md 为空，无法播种飞书配置");
            return 0;
        }
        Map<String, FeishuCanonicalParser.ParsedFeishu> parsed = FeishuCanonicalParser.parse(markdown);
        int version = promptProperties.getSeedVersion();
        int count = 0;
        for (FeishuCanonicalParser.ParsedFeishu item : parsed.values()) {
            if (!item.isValid()) {
                continue;
            }
            FeishuCountryConfig row = new FeishuCountryConfig();
            row.setCountryCode(item.countryCode);
            row.setAppToken(item.appToken);
            row.setTableId(item.tableId);
            row.setFieldMappingJson(JSON.toJSONString(item.fieldMapping));
            row.setSyncEnabled(item.syncEnabled);
            row.setSeedVersion(version);
            row.setUserModified(false);
            if (force) {
                feishuCountryConfigMapper.upsertForceSeed(row);
            } else {
                feishuCountryConfigMapper.upsertSystemSeed(row);
            }
            count++;
        }
        clearCache();
        log.info("飞书配置数据库播种完成: countries={}, force={}, seedVersion={}", count, force, version);
        return count;
    }

    public int importFromMarkdownContent(String markdown, boolean force) {
        Map<String, FeishuCanonicalParser.ParsedFeishu> parsed = FeishuCanonicalParser.parse(markdown);
        int version = promptProperties.getSeedVersion();
        int count = 0;
        for (FeishuCanonicalParser.ParsedFeishu item : parsed.values()) {
            if (!item.isValid()) {
                continue;
            }
            FeishuCountryConfig row = new FeishuCountryConfig();
            row.setCountryCode(item.countryCode);
            row.setAppToken(item.appToken);
            row.setTableId(item.tableId);
            row.setFieldMappingJson(JSON.toJSONString(item.fieldMapping));
            row.setSyncEnabled(true);
            row.setSeedVersion(version);
            row.setUserModified(false);
            if (force) {
                feishuCountryConfigMapper.upsertForceSeed(row);
            } else {
                feishuCountryConfigMapper.upsertSystemSeed(row);
            }
            count++;
        }
        clearCache();
        return count;
    }

    private Map<String, Object> loadConfigMapFromDb(String effectiveCountry) {
        FeishuCountryConfig row = feishuCountryConfigMapper.selectByCountry(effectiveCountry);
        if (row == null && !"default".equalsIgnoreCase(effectiveCountry)) {
            row = feishuCountryConfigMapper.selectByCountry("default");
        }
        Map<String, Object> config = new HashMap<>();
        if (row == null) {
            config.put("appToken", "");
            config.put("tableId", "");
            config.put("fieldMapping", new ArrayList<>());
            config.put("syncEnabled", true);
            return config;
        }
        config.put("appToken", row.getAppToken() != null ? row.getAppToken() : "");
        config.put("tableId", row.getTableId() != null ? row.getTableId() : "");
        config.put("fieldMapping", parseFieldMappingJson(row.getFieldMappingJson()));
        config.put("syncEnabled", row.isSyncEnabled());
        return config;
    }

    private List<Map<String, Object>> parseFieldMappingJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseObject(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析 field_mapping JSON 失败", e);
            return new ArrayList<>();
        }
    }

    private void clearCache() {
        configCache.clear();
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            return "default";
        }
        return "default".equalsIgnoreCase(country.trim()) ? "default" : country.trim().toUpperCase();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String readCanonicalResource() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("base-config/feishu.md")) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取内置 feishu.md 失败", e);
            return null;
        }
    }
}
