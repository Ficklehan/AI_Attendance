package com.attendance.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.Result;
import com.attendance.config.CountryCatalog;
import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.dto.CountryConfigBundle;
import com.attendance.dto.RecognitionEngineConfigDTO;
import com.attendance.dto.request.RecognitionEngineUpdateRequest;
import com.attendance.dto.request.SystemConfigRequest;
import com.attendance.dto.response.SystemConfigDTO;
import com.attendance.service.ConfigService;
import com.attendance.service.ConfirmValidationService;
import com.attendance.service.FeishuCountryConfigService;
import com.attendance.service.NightShiftConfigService;
import com.attendance.service.MarkdownConfigService;
import com.attendance.service.PluginConfigService;
import com.attendance.service.RecognitionEngineConfigService;
import com.attendance.service.VisionPromptService;
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
    private VisionPromptService visionPromptService;

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

    @Autowired
    private RecognitionEngineConfigService recognitionEngineConfigService;

    private void requireAdmin() {
        adminAuthService.requireAdmin();
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
    
    @PostMapping("/current-country")
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
    
    /** 视觉识别提示词（整表读/页头/表头列名/时钟列/姓名列），全局配置，改完即生效。 */
    @GetMapping("/vision-prompts")
    public Result<JSONArray> getVisionPrompts() {
        requireAdmin();
        return Result.success(visionPromptService.listAll());
    }

    @PostMapping("/vision-prompts")
    public Result<Void> updateVisionPrompts(@RequestBody JSONObject request) {
        requireAdmin();
        try {
            visionPromptService.saveAll(request);
            return Result.success(null, "视觉识别提示词更新成功");
        } catch (Exception e) {
            log.error("更新视觉识别提示词失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/feishu")
    public Result<Map<String, Object>> getFeishuConfig(@RequestParam(required = false, defaultValue = "default") String country) {
        requireAdmin();
        return Result.success(configService.getFeishuConfig(country));
    }
    
    @PostMapping("/feishu")
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
    
    @PostMapping("/field-mapping")
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
     * 路径使用 /runtime/ 前缀，避免与 POST /config/{configKey}/delete 单段路径冲突导致 405。
     */
    @GetMapping("/runtime/night-shift")
    public Result<NightShiftConfigDTO> getNightShiftConfig(
            @RequestParam(required = false, defaultValue = "default") String country) {
        return Result.success(nightShiftConfigService.getConfigForCountry(country));
    }

    @PostMapping("/system")
    public Result<SystemConfigDTO> updateSystemConfig(@Validated @RequestBody SystemConfigRequest request) {
        requireAdmin();
        pluginConfigService.updateSystemConfig(request);
        return Result.success(pluginConfigService.getSystemConfig());
    }

    /**
     * 识别模型引擎配置（MiMo / DeepSeek），仅管理员可读写。
     */
    @GetMapping("/recognition-engine")
    public Result<RecognitionEngineConfigDTO> getRecognitionEngineConfig() {
        requireAdmin();
        return Result.success(recognitionEngineConfigService.getConfig());
    }

    @PostMapping("/recognition-engine")
    public Result<RecognitionEngineConfigDTO> updateRecognitionEngineConfig(
            @Validated @RequestBody RecognitionEngineUpdateRequest request) {
        requireAdmin();
        recognitionEngineConfigService.saveEngine(request.getEngine());
        return Result.success(recognitionEngineConfigService.getConfig());
    }
}
