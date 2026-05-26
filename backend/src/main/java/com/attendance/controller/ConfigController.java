package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.service.ConfigService;
import com.attendance.service.MarkdownConfigService;
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

    @GetMapping
    public Result<Map<String, String>> getAllConfigs() {
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
        String country = request.get("country");
        if (country == null || country.trim().isEmpty()) {
            return Result.error(400, "国家不能为空");
        }
        configService.setCurrentCountry(country);
        return Result.success(null, "当前工作国家设置成功");
    }
    
    @GetMapping("/countries")
    public Result<List<String>> getAllCountries() {
        return Result.success(configService.getAllCountries());
    }
    
    @GetMapping("/ai-prompt")
    public Result<Map<String, String>> getAiPrompts(@RequestParam(required = false, defaultValue = "default") String country) {
        Map<String, String> prompts = new HashMap<>();
        prompts.put("ai_prompt", configService.getAiPrompt(country));
        prompts.put("continue_prompt", configService.getContinuePrompt(country));
        prompts.put("country", country);
        return Result.success(prompts);
    }
    
    @PutMapping("/ai-prompt")
    public Result<Void> updateAiPrompt(@RequestBody Map<String, String> request) {
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
        return Result.success(configService.getFeishuConfig(country));
    }
    
    @PutMapping("/feishu")
    public Result<Void> updateFeishuConfig(@RequestBody Map<String, String> request) {
        try {
            String country = request.getOrDefault("country", "default");
            String appToken = request.get("bitable_app_token");
            String tableId = request.get("bitable_table_id");
            String fieldMapping = request.get("field_mapping");
            markdownConfigService.updateFeishuConfig(country, appToken, tableId, fieldMapping);
            return Result.success(null, "飞书配置更新成功");
        } catch (Exception e) {
            log.error("更新飞书配置失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/field-mapping")
    public Result<List<Map<String, Object>>> getFieldMapping(@RequestParam(required = false, defaultValue = "default") String country) {
        return Result.success(configService.getFieldMapping(country));
    }
    
    @PutMapping("/field-mapping")
    public Result<Void> updateFieldMapping(@RequestBody Map<String, Object> request) {
        try {
            String country = request.get("country") != null ? request.get("country").toString() : "default";
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) request.get("field_mapping");
            
            StringBuilder fieldMappingYaml = new StringBuilder("field_mapping:\n");
            for (Map<String, Object> mapping : mappings) {
                fieldMappingYaml.append("  - aiField: '").append(mapping.get("aiField")).append("'\n");
                fieldMappingYaml.append("    feishuField: '").append(mapping.get("feishuField")).append("'\n");
                fieldMappingYaml.append("    type: '").append(mapping.get("type")).append("'\n");
                fieldMappingYaml.append("    required: ").append(mapping.get("required")).append("\n");
                fieldMappingYaml.append("    description: '").append(mapping.get("description")).append("'\n");
            }
            
            Map<String, Object> feishuConfig = configService.getFeishuConfig(country);
            String appToken = feishuConfig.get("appToken") != null ? feishuConfig.get("appToken").toString() : "";
            String tableId = feishuConfig.get("tableId") != null ? feishuConfig.get("tableId").toString() : "";
            
            markdownConfigService.updateFeishuConfig(country, appToken, tableId, fieldMappingYaml.toString());
            return Result.success(null, "字段映射更新成功");
        } catch (Exception e) {
            log.error("更新字段映射失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    @PutMapping
    public Result<Void> updateConfig(@RequestBody Map<String, String> request) {
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
        configService.deleteConfig(configKey);
        return Result.success(null, "配置删除成功");
    }
    
    @PostMapping("/reload")
    public Result<Void> reloadConfigs() {
        try {
            markdownConfigService.loadConfigs();
            log.info("配置文件已重新加载");
            return Result.success(null, "配置文件已重新加载成功");
        } catch (Exception e) {
            log.error("重新加载配置文件失败", e);
            return Result.error(500, "重新加载配置文件失败: " + e.getMessage());
        }
    }
}
