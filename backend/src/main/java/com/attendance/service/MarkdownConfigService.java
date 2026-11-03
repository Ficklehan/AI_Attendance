package com.attendance.service;

import com.attendance.config.ConfigPathResolver;
import com.attendance.dto.CountryConfigBundle;
import com.attendance.util.FeishuCanonicalParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MarkdownConfigService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownConfigService.class);

    @Autowired
    private ConfigPathResolver configPathResolver;

    @Autowired
    private FeishuCountryConfigService feishuCountryConfigService;

    private String feishuContent;
    private String countriesContent;

    private String currentCountry = "default";
    private long feishuFileLastModified = -1L;

    @PostConstruct
    public void init() {
        loadConfigs();
    }

    public void loadConfigs() {
        try {
            log.info("开始加载配置文件...");
            log.info("当前工作目录: {}", System.getProperty("user.dir"));
            log.info("配置基础路径: {}", configPathResolver.getBaseConfigDir());

            feishuContent = readFile("feishu.md");
            feishuFileLastModified = lastModified("feishu.md");
            countriesContent = readFile("countries.md");

            log.info("feishu.md 长度: {}", feishuContent.length());
            log.info("countries.md 长度: {}", countriesContent.length());
            log.info("配置文件加载成功");
        } catch (IOException e) {
            log.error("加载配置文件失败", e);
        }
    }

    private void ensureFeishuFresh() {
        try {
            long mtime = lastModified("feishu.md");
            if (mtime != feishuFileLastModified) {
                feishuContent = readFile("feishu.md");
                feishuFileLastModified = mtime;
            }
        } catch (IOException e) {
            log.warn("刷新 feishu.md 失败，继续使用内存缓存", e);
        }
    }

    private long lastModified(String filename) throws IOException {
        Path path = configPathResolver.resolveFile(filename);
        return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
    }

    private String readFile(String filename) throws IOException {
        Path path = configPathResolver.resolveFile(filename);
        log.info("读取文件: {}, 绝对路径: {}", filename, path.toAbsolutePath());

        if (!Files.exists(path)) {
            log.warn("配置文件不存在: {}", path);
            return "";
        }
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        log.info("文件 {} 读取成功，长度: {}", filename, content.length());
        return content;
    }

    /** 识别/飞书配置国家：仅规范化，不再按提示词回退 default。 */
    public String resolveEffectiveCountry(String country) {
        return normalizeCountryCode(country);
    }

    /** 飞书多维表：所选国家无独立数据库配置时回退 default。 */
    public String resolveEffectiveFeishuCountry(String country) {
        return feishuCountryConfigService.resolveEffectiveFeishuCountry(country);
    }

    public CountryConfigBundle getCountryConfigBundle(String country) {
        String request = normalizeCountryCode(country);
        String effectiveFeishu = resolveEffectiveFeishuCountry(request);

        CountryConfigBundle bundle = new CountryConfigBundle();
        bundle.setRequestCountry(request);
        bundle.setEffectiveFeishuCountry(effectiveFeishu);
        bundle.setFeishuFromGlobalFallback(!"default".equalsIgnoreCase(request)
                && "default".equalsIgnoreCase(effectiveFeishu));

        Map<String, Object> feishu = getFeishuConfig(request);
        bundle.setAppToken((String) feishu.get("appToken"));
        bundle.setTableId((String) feishu.get("tableId"));
        bundle.setSyncEnabled(feishuCountryConfigService.isSyncEnabled(request));
        Object mapping = feishu.get("fieldMapping");
        if (mapping instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) mapping;
            bundle.setFieldMapping(list);
        }
        return bundle;
    }

    private static String normalizeCountryCode(String country) {
        if (country == null || country.trim().isEmpty()) {
            return "default";
        }
        return "default".equalsIgnoreCase(country.trim()) ? "default" : country.trim().toUpperCase();
    }

    public boolean hasCountryConfig(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return true;
        }
        return feishuCountryConfigService.hasCountryRow(country.trim().toUpperCase());
    }

    public Map<String, Object> getFeishuConfig() {
        return getFeishuConfig(currentCountry);
    }

    public Map<String, Object> getFeishuConfig(String country) {
        log.info("getFeishuConfig(DB) country={}", country);
        return feishuCountryConfigService.getFeishuConfig(country);
    }

    public List<Map<String, Object>> getFieldMapping() {
        return getFieldMapping(currentCountry);
    }

    public List<Map<String, Object>> getFieldMapping(String country) {
        return feishuCountryConfigService.getFieldMapping(country);
    }

    public void setCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            this.currentCountry = "default";
            return;
        }
        if ("default".equalsIgnoreCase(country.trim())) {
            this.currentCountry = "default";
        } else {
            this.currentCountry = country.trim().toUpperCase();
        }
    }

    public String getCurrentCountry() {
        return currentCountry;
    }

    public List<String> getAllCountries() {
        List<String> countries = new ArrayList<>();
        countries.add("default");
        try {
            for (String code : feishuCountryConfigService.listCountryCodes()) {
                if (!"default".equalsIgnoreCase(code) && !countries.contains(code)) {
                    countries.add(code);
                }
            }
        } catch (Exception e) {
            log.warn("从数据库读取飞书国家列表失败", e);
        }
        return countries;
    }

    public String getFeishuContent() {
        return feishuContent;
    }

    public String getCountriesContent() {
        return countriesContent;
    }

    public void updateFeishuConfig(String appToken, String tableId, String fieldMapping) {
        updateFeishuConfig("default", appToken, tableId, fieldMapping);
    }

    public void updateFeishuConfig(String country, String appToken, String tableId, String fieldMappingYaml) {
        List<Map<String, Object>> mappings = new ArrayList<>();
        if (fieldMappingYaml != null && !fieldMappingYaml.trim().isEmpty()) {
            String raw = fieldMappingYaml.trim();
            if (raw.startsWith("[")) {
                mappings = com.alibaba.fastjson.JSON.parseObject(raw,
                        new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {});
            } else {
                if (!raw.contains("field_mapping:")) {
                    raw = "field_mapping:\n" + raw;
                }
                mappings = FeishuCanonicalParser.extractFieldMapping(raw);
            }
        }
        feishuCountryConfigService.saveUserConfig(country, appToken, tableId, mappings, null);
        log.info("飞书配置已写入数据库: country={}", country);
    }

    public void updateFeishuConfig(String country,
                                   String appToken,
                                   String tableId,
                                   List<Map<String, Object>> fieldMapping,
                                   Boolean syncEnabled) {
        feishuCountryConfigService.saveUserConfig(country, appToken, tableId, fieldMapping, syncEnabled);
    }
}
