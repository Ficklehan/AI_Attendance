package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.config.CountryCatalog;
import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.dto.CountryConfigBundle;
import com.attendance.dto.request.SystemConfigRequest;
import com.attendance.dto.response.SystemConfigDTO;
import com.attendance.service.ConfigService;
import com.attendance.service.ConfirmValidationService;
import com.attendance.service.FeishuCountryConfigService;
import com.attendance.service.NightShiftConfigService;
import com.attendance.service.MarkdownConfigService;
import com.attendance.service.PluginConfigService;
import com.attendance.service.RecognitionPromptGuard;
import com.attendance.service.RecognitionPromptService;
import com.attendance.service.RecognitionQualityGuard;
import com.attendance.security.AdminAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/config")
@Validated
public class ConfigController {
    
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    private ConfigService configService;
    
    @Autowired
    private MarkdownConfigService markdownConfigService;

    @Autowired
    private RecognitionPromptGuard recognitionPromptGuard;

    @Autowired
    private RecognitionQualityGuard recognitionQualityGuard;

    @Autowired
    private RecognitionPromptService recognitionPromptService;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private PluginConfigService pluginConfigService;

    @Autowired
    private ConfirmValidationService confirmValidationService;

    @Autowired
    private NightShiftConfigService nightShiftConfigService;

    @Autowired
    private FeishuCountryConfigService feishuCountryConfigService;

    private void requireAdmin() {
        adminAuthService.requireAdmin();
    }

    /**
     * 查看识别时实际发给 MiMo 的提示词（与 PC/小程序上传共用逻辑），便于核对 prompts.md 是否生效。
     */
    @GetMapping("/recognition-prompt-preview")
    public Result<Map<String, Object>> recognitionPromptPreview(
            @RequestParam(required = false, defaultValue = "default") String country) {
        requireAdmin();
        CountryConfigBundle bundle = configService.getCountryConfigBundle(country);
        String fromConfig = bundle.getAiPrompt();
        String forApi = recognitionPromptGuard.preparePromptForApi(fromConfig, recognitionQualityGuard);
        Map<String, Object> body = new HashMap<>();
        body.put("requestCountry", bundle.getRequestCountry());
        body.put("effectiveCountry", bundle.getEffectivePromptCountry());
        body.put("effectivePromptCountry", bundle.getEffectivePromptCountry());
        body.put("effectiveFeishuCountry", bundle.getEffectiveFeishuCountry());
        body.put("promptFromGlobalFallback", bundle.isPromptFromGlobalFallback());
        body.put("feishuFromGlobalFallback", bundle.isFeishuFromGlobalFallback());
        body.put("promptSection", bundle.getPromptSection());
        body.put("feishuAppTokenConfigured", bundle.getAppToken() != null && !bundle.getAppToken().trim().isEmpty());
        body.put("feishuTableIdConfigured", bundle.getTableId() != null && !bundle.getTableId().trim().isEmpty());
        body.put("fieldMappingCount", bundle.getFieldMapping() != null ? bundle.getFieldMapping().size() : 0);
        body.put("feishuSyncEnabled", bundle.isSyncEnabled());
        body.put("configPromptLength", fromConfig != null ? fromConfig.length() : 0);
        body.put("apiPromptLength", forApi != null ? forApi.length() : 0);
        body.put("includesExampleBlock", fromConfig != null && fromConfig.contains("示例"));
        body.put("configPromptPreview", truncate(fromConfig, 1200));
        body.put("apiPromptPreview", truncate(forApi, 1200));
        return Result.success(body);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String one = text.replace('\r', ' ').replace('\n', ' ');
        return one.length() <= max ? one : one.substring(0, max) + "...";
    }

    @GetMapping
    public Result<Map<String, String>> getAllConfigs() {
        requireAdmin();
        return Result.success(configService.getAllConfigs());
    }
    
    @GetMapping("/current-country")
    public Result<Map<String, String>> getCurrentCountry() {
        Map<String, String> result = new HashMap<>();
        result.put("country", configService.getCurrentCountry());
        return Result.success(result);
    }
    
    @PutMapping("/current-country")
    public Result<Void> setCurrentCountry(@RequestBody Map<String, String> request) {
        requireAdmin();
        String country = request.get("country");
        if (country == null || country.trim().isEmpty()) {
            return Result.error(400, "国家不能为空");
        }
        if (!CountryCatalog.isSupported(country)) {
            return Result.error(400, "不支持的国家配置: " + country);
        }
        configService.setCurrentCountry(country);
        return Result.success(null, "当前工作国家设置成功");
    }
    
    @GetMapping("/countries")
    public Result<List<String>> getAllCountries() {
        return Result.success(configService.getAllCountries());
    }

    /** 与 PC Config.vue 一致的国家选项（code / flag / name） */
    @GetMapping("/country-options")
    public Result<List<Map<String, String>>> getCountryOptions() {
        return Result.success(CountryCatalog.OPTIONS);
    }

    /**
     * 按国家返回 AI 提示词 + 飞书多维表配置（各自可回退全局 default）。
     */
    @GetMapping("/country-bundle")
    public Result<CountryConfigBundle> getCountryBundle(
            @RequestParam(required = false, defaultValue = "default") String country) {
        CountryConfigBundle bundle = configService.getCountryConfigBundle(country);
        if (adminAuthService.isCurrentUserAdmin()) {
            return Result.success(bundle);
        }
        return Result.success(bundle.toPublicSummary());
    }
    
    @GetMapping("/ai-prompt")
    public Result<Map<String, String>> getAiPrompts(@RequestParam(required = false, defaultValue = "default") String country) {
        requireAdmin();
        Map<String, String> prompts = new HashMap<>();
        prompts.put("ai_prompt", configService.getAiPrompt(country));
        prompts.put("continue_prompt", configService.getContinuePrompt(country));
        prompts.put("country", country);
        prompts.put("legacy_prompt", String.valueOf(markdownConfigService.isCurrentPromptsLegacy()));
        return Result.success(prompts);
    }

    @GetMapping("/prompt-status")
    public Result<Map<String, Object>> getPromptStatus() {
        requireAdmin();
        String content = recognitionPromptService.getAiPrompt("default");
        Map<String, Object> body = new HashMap<>();
        body.put("legacy", markdownConfigService.isCurrentPromptsLegacy()
                || recognitionPromptService.isMissingPageNumPromptInDatabase());
        body.put("hasNewFields", content != null && content.contains("Pays,Entrepot"));
        body.put("hasPageNum", content != null && content.contains("PAGE_NUM"));
        body.put("storage", "database");
        body.put("rowCount", recognitionPromptService.countRows());
        return Result.success(body);
    }
    
    @PutMapping("/ai-prompt")
    public Result<Void> updateAiPrompt(@RequestBody Map<String, String> request) {
        requireAdmin();
        try {
            String country = request.getOrDefault("country", "default");
            String aiPrompt = request.get("ai_prompt");
            String continuePrompt = request.get("continue_prompt");
            markdownConfigService.updatePrompt(country, aiPrompt, continuePrompt);
            return Result.success(null, "AI提示词更新成功");
        } catch (Exception e) {
            log.error("更新AI提示词失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/feishu")
    public Result<Map<String, Object>> getFeishuConfig(@RequestParam(required = false, defaultValue = "default") String country) {
        requireAdmin();
        return Result.success(configService.getFeishuConfig(country));
    }
    
    @PutMapping("/feishu")
    public Result<Void> updateFeishuConfig(@RequestBody Map<String, Object> request) {
        requireAdmin();
        try {
            String country = request.get("country") != null ? request.get("country").toString() : "default";
            String appToken = request.get("bitable_app_token") != null ? request.get("bitable_app_token").toString() : "";
            String tableId = request.get("bitable_table_id") != null ? request.get("bitable_table_id").toString() : "";
            Boolean syncEnabled = parseSyncEnabled(request.get("sync_enabled"));
            List<Map<String, Object>> mappings = parseFieldMappings(request.get("field_mapping"));
            if (mappings.isEmpty()) {
                mappings = configService.getFieldMapping(country);
            }
            markdownConfigService.updateFeishuConfig(country, appToken, tableId, mappings, syncEnabled);
            return Result.success(null, "飞书配置更新成功");
        } catch (Exception e) {
            log.error("更新飞书配置失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/field-mapping")
    public Result<List<Map<String, Object>>> getFieldMapping(@RequestParam(required = false, defaultValue = "default") String country) {
        requireAdmin();
        return Result.success(configService.getFieldMapping(country));
    }
    
    @PutMapping("/field-mapping")
    public Result<Void> updateFieldMapping(@RequestBody Map<String, Object> request) {
        requireAdmin();
        try {
            String country = request.get("country") != null ? request.get("country").toString() : "default";
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) request.get("field_mapping");
            feishuCountryConfigService.saveFieldMapping(country, mappings);
            return Result.success(null, "字段映射更新成功");
        } catch (Exception e) {
            log.error("更新字段映射失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    @PutMapping
    public Result<Void> updateConfig(@RequestBody Map<String, String> request) {
        requireAdmin();
        String configKey = request.get("configKey");
        String configValue = request.get("configValue");
        
        if (configKey == null || configValue == null) {
            return Result.error(400, "configKey 和 configValue 不能为空");
        }
        
        configService.updateConfig(configKey, configValue);
        return Result.success(null, "配置更新成功");
    }

    @DeleteMapping("/{configKey}")
    public Result<Void> deleteConfig(@PathVariable String configKey) {
        requireAdmin();
        configService.deleteConfig(configKey);
        return Result.success(null, "配置删除成功");
    }
    
    /**
     * 仅刷新文件型配置（飞书连接、国家可选项等）。
     * 识别提示词已改为数据库唯一数据源，避免误覆盖用户编辑。
     */
    @PostMapping("/reload")
    public Result<Void> reloadConfigs() {
        requireAdmin();
        try {
            markdownConfigService.loadConfigs();
            log.info("配置已重新加载（飞书配置以数据库为准，未从 md 覆盖）");
            return Result.success(null, "配置已刷新");
        } catch (Exception e) {
            log.error("重新加载配置失败", e);
            return Result.error(500, "重新加载配置失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseFieldMappings(Object raw) {
        if (raw == null) {
            return new java.util.ArrayList<>();
        }
        if (raw instanceof List) {
            return (List<Map<String, Object>>) raw;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        if (text.startsWith("[")) {
            return com.alibaba.fastjson.JSON.parseObject(text,
                    new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {});
        }
        if (!text.contains("field_mapping:")) {
            text = "field_mapping:\n" + text;
        }
        return com.attendance.util.FeishuCanonicalParser.extractFieldMapping(text);
    }

    private static Boolean parseSyncEnabled(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    /**
     * 重置识别提示词为标准模板（强制覆盖所有国家）。
     * 建议在确认后使用。
     */
    @PostMapping("/reset-prompts")
    public Result<Void> resetPromptsToStandard() {
        requireAdmin();
        try {
            int seeded = recognitionPromptService.seedFromCanonical(true);
            log.info("识别提示词已重置为标准模板: countries={}", seeded);
            return Result.success(null, "已将识别提示词重置为标准模板（" + seeded + " 个国家）");
        } catch (Exception e) {
            log.error("重置提示词失败", e);
            return Result.error(500, "重置提示词失败: " + e.getMessage());
        }
    }

    @GetMapping("/system")
    public Result<SystemConfigDTO> getSystemConfig() {
        requireAdmin();
        return Result.success(pluginConfigService.getSystemConfig());
    }

    /**
     * 确认任务必填校验配置（任务编辑/确认页只读，任意登录用户可拉取）。
     */
    @GetMapping("/confirm-validation")
    public Result<ConfirmValidationConfigDTO> getConfirmValidationConfig() {
        return Result.success(confirmValidationService.getConfig());
    }

    /**
     * 夜班判定规则（任务展示/标记推断只读，任意登录用户可拉取）。
     * 路径使用 /runtime/ 前缀，避免与 DELETE /config/{configKey} 单段路径冲突导致 405。
     */
    @GetMapping("/runtime/night-shift")
    public Result<NightShiftConfigDTO> getNightShiftConfig(
            @RequestParam(required = false, defaultValue = "default") String country) {
        return Result.success(nightShiftConfigService.getConfigForCountry(country));
    }

    @PutMapping("/system")
    public Result<SystemConfigDTO> updateSystemConfig(@Validated @RequestBody SystemConfigRequest request) {
        requireAdmin();
        pluginConfigService.updateSystemConfig(request);
        return Result.success(pluginConfigService.getSystemConfig());
    }
}
