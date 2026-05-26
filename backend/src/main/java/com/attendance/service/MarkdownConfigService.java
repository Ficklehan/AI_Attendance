package com.attendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(MarkdownConfigService.class);
    
    private static final String CONFIG_BASE_PATH = "../base-config";
    
    private String promptsContent;
    private String feishuContent;
    private String countriesContent;
    
    private String currentCountry = "default";
    
    @PostConstruct
    public void init() {
        loadConfigs();
    }
    
    public void loadConfigs() {
        try {
            log.info("开始加载配置文件...");
            log.info("当前工作目录: {}", System.getProperty("user.dir"));
            log.info("配置基础路径: {}", CONFIG_BASE_PATH);
            
            promptsContent = readFile("prompts.md");
            feishuContent = readFile("feishu.md");
            countriesContent = readFile("countries.md");
            
            log.info("prompts.md 长度: {}", promptsContent.length());
            log.info("feishu.md 长度: {}", feishuContent.length());
            log.info("countries.md 长度: {}", countriesContent.length());
            log.info("配置文件加载成功");
        } catch (IOException e) {
            log.error("加载配置文件失败", e);
        }
    }
    
    private String readFile(String filename) throws IOException {
        Path path = Paths.get(CONFIG_BASE_PATH, filename);
        log.info("读取文件: {}, 绝对路径: {}", filename, path.toAbsolutePath());
        
        if (!Files.exists(path)) {
            log.warn("配置文件不存在: {}", path);
            return "";
        }
        String content = Files.readString(path);
        log.info("文件 {} 读取成功，长度: {}", filename, content.length());
        return content;
    }
    
    public String getAiPrompt() {
        return getAiPrompt(currentCountry);
    }
    
    public String getAiPrompt(String country) {
        log.info("getAiPrompt 被调用，country={}", country);
        if ("default".equalsIgnoreCase(country)) {
            String prompt = extractSection(promptsContent, "主要识别提示词", "```markdown", "```");
            if (prompt != null) {
                log.info("最终提示词长度: {}, 前200字符: {}", prompt.length(), prompt.substring(0, Math.min(200, prompt.length())));
            }
            return prompt;
        }
        
        // 先尝试直接匹配 "### 国家代码 - 识别提示词"
        String sectionName = country + " - 识别提示词";
        log.info("准备提取 section: {}", sectionName);
        String prompt = extractSection(promptsContent, sectionName, "```markdown", "```");
        
        // 如果没找到，尝试查找带括号的格式 "### 国家名 (国家代码) - 识别提示词"
        if (prompt == null || prompt.trim().isEmpty()) {
            log.info("提取失败，尝试查找带括号的格式");
            prompt = findPromptSectionByCountryCode(promptsContent, country, "识别提示词");
        }
        
        // 最后 fallback 到默认
        if (prompt == null || prompt.trim().isEmpty()) {
            log.info("提取失败，尝试提取默认的 '主要识别提示词'");
            prompt = extractSection(promptsContent, "主要识别提示词", "```markdown", "```");
        }
        
        if (prompt != null) {
            log.info("最终提示词长度: {}, 前200字符: {}", prompt.length(), prompt.substring(0, Math.min(200, prompt.length())));
        }
        return prompt;
    }
    
    public String getContinuePrompt() {
        return getContinuePrompt(currentCountry);
    }
    
    public String getContinuePrompt(String country) {
        if ("default".equalsIgnoreCase(country)) {
            return extractSection(promptsContent, "继续输出提示词", "```markdown", "```");
        }
        
        // 先尝试直接匹配
        String sectionName = country + " - 继续提示词";
        String prompt = extractSection(promptsContent, sectionName, "```markdown", "```");
        
        // 如果没找到，尝试查找带括号的格式
        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = findPromptSectionByCountryCode(promptsContent, country, "继续提示词");
        }
        
        // 最后 fallback 到默认
        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = extractSection(promptsContent, "继续输出提示词", "```markdown", "```");
        }
        
        return prompt;
    }
    
    private String findPromptSectionByCountryCode(String content, String countryCode, String sectionType) {
        // 匹配类似 "### 中国 (CN) - 识别提示词" 这种格式的章节
        Pattern pattern = Pattern.compile("###+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)[^\\n]*" + Pattern.quote(sectionType) + "[^\\n]*\\n(.*?)```markdown(.*?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        
        return null;
    }
    
    public Map<String, Object> getFeishuConfig() {
        return getFeishuConfig(currentCountry);
    }
    
    public Map<String, Object> getFeishuConfig(String country) {
        Map<String, Object> config = new HashMap<>();
        
        String targetSection = "default".equalsIgnoreCase(country) ? "全局默认配置" : country;
        // 先尝试直接匹配国家代码
        String yamlContent = extractSection(feishuContent, targetSection, "```yaml", "```");
        
        // 如果没找到，尝试查找带括号的格式 "### 荷兰 (NL)"
        if ((yamlContent == null || yamlContent.trim().isEmpty()) && !"default".equalsIgnoreCase(country)) {
            // 查找包含该国家代码的章节
            yamlContent = findFeishuSectionByCountryCode(feishuContent, country);
        }
        
        // 最后 fallback 到默认
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            targetSection = "全局默认配置";
            yamlContent = extractSection(feishuContent, targetSection, "```yaml", "```");
        }
        
        if (yamlContent != null) {
            config.put("yaml", yamlContent);
            config.put("appToken", extractYamlValue(yamlContent, "bitable_app_token"));
            config.put("tableId", extractYamlValue(yamlContent, "bitable_table_id"));
            config.put("fieldMapping", extractFieldMapping(yamlContent));
        }
        
        return config;
    }
    
    private String findFeishuSectionByCountryCode(String content, String countryCode) {
        // 匹配类似 "### 荷兰 (NL)" 这种格式的章节
        Pattern pattern = Pattern.compile("###+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)[^\\n]*\\n(.*?)```yaml(.*?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        
        return null;
    }
    
    public List<Map<String, Object>> getFieldMapping() {
        return getFieldMapping(currentCountry);
    }
    
    public List<Map<String, Object>> getFieldMapping(String country) {
        Map<String, Object> config = getFeishuConfig(country);
        Object mapping = config.get("fieldMapping");
        if (mapping instanceof List) {
            return (List<Map<String, Object>>) mapping;
        }
        return new ArrayList<>();
    }
    
    public void setCountry(String country) {
        this.currentCountry = country.toUpperCase();
    }
    
    public String getCurrentCountry() {
        return currentCountry;
    }
    
    public boolean hasCountryConfig(String country) {
        String targetSection = "default".equalsIgnoreCase(country) ? "全局默认配置" : "### " + country;
        String content = extractSection(feishuContent, targetSection, "```yaml", "```");
        return content != null && !content.trim().isEmpty();
    }
    
    public List<String> getAllCountries() {
        List<String> countries = new ArrayList<>();
        countries.add("default");
        
        // 匹配 "### 荷兰 (NL)" 这种格式
        Pattern pattern = Pattern.compile("###+\\s*[^\\n]*\\(([A-Z]{2})\\)");
        Matcher matcher = pattern.matcher(feishuContent);
        
        while (matcher.find()) {
            String country = matcher.group(1);
            if (!countries.contains(country)) {
                countries.add(country);
            }
        }
        
        // 也匹配 "### NL" 这种简单格式
        Pattern simplePattern = Pattern.compile("###\\s*([A-Z]{2})");
        Matcher simpleMatcher = simplePattern.matcher(feishuContent);
        
        while (simpleMatcher.find()) {
            String country = simpleMatcher.group(1);
            if (!countries.contains(country)) {
                countries.add(country);
            }
        }
        
        return countries;
    }
    
    private String extractSection(String content, String sectionName, String codeStart, String codeEnd) {
        log.info("extractSection 被调用，sectionName={}, codeStart={}, codeEnd={}", sectionName, codeStart, codeEnd);
        
        if (content == null || content.isEmpty()) {
            log.warn("extractSection: content 为空");
            return null;
        }
        
        log.info("原始 content 长度: {}, 前300字符: {}", content.length(), content.substring(0, Math.min(300, content.length())));
        
        // 支持匹配 ## 或 ###
        String regex = "(?:##+\\s*)" + Pattern.quote(sectionName) + "(.*?)(?=##|$)";
        log.info("使用正则表达式: {}", regex);
        Pattern sectionPattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher sectionMatcher = sectionPattern.matcher(content);
        
        if (sectionMatcher.find()) {
            log.info("找到了匹配的 section");
            String sectionContent = sectionMatcher.group(1);
            log.info("提取的 sectionContent 长度: {}, 前300字符: {}", sectionContent.length(), sectionContent.substring(0, Math.min(300, sectionContent.length())));
            
            int startIdx = sectionContent.indexOf(codeStart);
            log.info("查找 codeStart '{}' 位置: {}", codeStart, startIdx);
            
            if (startIdx == -1) {
                log.info("没找到 codeStart，返回完整 sectionContent");
                return sectionContent.trim();
            }
            
            startIdx += codeStart.length();
            int endIdx = sectionContent.indexOf(codeEnd, startIdx);
            log.info("查找 codeEnd '{}' 位置: {}", codeEnd, endIdx);
            
            if (endIdx == -1) {
                log.info("没找到 codeEnd，返回从 startIdx 开始的内容");
                return sectionContent.substring(startIdx).trim();
            }
            
            String result = sectionContent.substring(startIdx, endIdx).trim();
            log.info("提取结果长度: {}, 前300字符: {}", result.length(), result.substring(0, Math.min(300, result.length())));
            return result;
        }
        
        log.warn("没找到匹配的 sectionName: {}", sectionName);
        return null;
    }
    
    private String extractYamlValue(String yaml, String key) {
        Pattern pattern = Pattern.compile(key + ":\\s*['\"]?([^'\"\\n]+)['\"]?");
        Matcher matcher = pattern.matcher(yaml);
        
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            return value.isEmpty() ? null : value;
        }
        
        return null;
    }
    
    private List<Map<String, Object>> extractFieldMapping(String yaml) {
        List<Map<String, Object>> mappings = new ArrayList<>();
        
        log.info("开始提取字段映射，yaml 内容长度: {}", yaml.length());
        log.info("yaml 内容前500字符: {}", yaml.substring(0, Math.min(500, yaml.length())));
        
        // 更灵活的正则表达式，逐行解析每个字段
        String[] lines = yaml.split("\\n");
        Map<String, Object> currentMapping = null;
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.startsWith("- aiField:")) {
                // 新字段开始
                if (currentMapping != null && currentMapping.containsKey("aiField")) {
                    mappings.add(currentMapping);
                }
                currentMapping = new HashMap<>();
                String value = line.substring("- aiField:".length()).trim();
                value = value.replaceAll("^['\"]|['\"]$", "");
                currentMapping.put("aiField", value);
            } else if (currentMapping != null && line.startsWith("feishuField:")) {
                String value = line.substring("feishuField:".length()).trim();
                value = value.replaceAll("^['\"]|['\"]$", "");
                currentMapping.put("feishuField", value);
            } else if (currentMapping != null && line.startsWith("type:")) {
                String value = line.substring("type:".length()).trim();
                value = value.replaceAll("^['\"]|['\"]$", "");
                currentMapping.put("type", value);
            } else if (currentMapping != null && line.startsWith("required:")) {
                String value = line.substring("required:".length()).trim();
                currentMapping.put("required", Boolean.parseBoolean(value));
            } else if (currentMapping != null && line.startsWith("description:")) {
                String value = line.substring("description:".length()).trim();
                value = value.replaceAll("^['\"]|['\"]$", "");
                currentMapping.put("description", value);
            }
        }
        
        // 添加最后一个字段
        if (currentMapping != null && currentMapping.containsKey("aiField")) {
            mappings.add(currentMapping);
        }
        
        log.info("提取到 {} 个字段映射", mappings.size());
        for (int i = 0; i < mappings.size(); i++) {
            log.info("字段映射 {}: {}", i, mappings.get(i));
        }
        
        return mappings;
    }
    
    public String getPromptContent() {
        return promptsContent;
    }
    
    public String getFeishuContent() {
        return feishuContent;
    }
    
    public String getCountriesContent() {
        return countriesContent;
    }
    
    public void updatePrompt(String aiPrompt, String continuePrompt) throws IOException {
        updatePrompt("default", aiPrompt, continuePrompt);
    }
    
    public void updatePrompt(String country, String aiPrompt, String continuePrompt) throws IOException {
        String content = promptsContent;
        
        String aiSectionName = "default".equalsIgnoreCase(country) ? "主要识别提示词" : country + " - 识别提示词";
        String continueSectionName = "default".equalsIgnoreCase(country) ? "继续输出提示词" : country + " - 继续提示词";
        
        content = updateSection(content, aiSectionName, aiPrompt, country);
        content = updateSection(content, continueSectionName, continuePrompt, country);
        
        Path path = Paths.get(CONFIG_BASE_PATH, "prompts.md");
        Files.writeString(path, content);
        this.promptsContent = content;
        
        log.info("提示词配置已更新: country={}", country);
    }
    
    public void updateFeishuConfig(String appToken, String tableId, String fieldMapping) throws IOException {
        updateFeishuConfig("default", appToken, tableId, fieldMapping);
    }
    
    public void updateFeishuConfig(String country, String appToken, String tableId, String fieldMapping) throws IOException {
        String content = feishuContent;
        
        String sectionName = "default".equalsIgnoreCase(country) ? "全局默认配置" : country;
        
        String newSection = String.format("```yaml\nbitable_app_token: '%s'\nbitable_table_id: '%s'\n%s\n```", 
                appToken != null ? appToken : "", 
                tableId != null ? tableId : "",
                fieldMapping != null ? fieldMapping : "");
        
        // 先尝试更新现有的章节
        content = updateSection(content, sectionName, newSection, country);
        
        Path path = Paths.get(CONFIG_BASE_PATH, "feishu.md");
        Files.writeString(path, content);
        this.feishuContent = content;
        
        log.info("飞书配置已更新: country={}", country);
    }
    
    private String updateSection(String content, String sectionName, String newContent, String country) {
        // 先尝试查找现有章节（支持多种格式）
        String foundSectionHeader = null;
        int foundSectionStart = -1;
        int foundSectionEnd = -1;
        
        // 尝试匹配多种格式
        String[] patternsToTry;
        if ("default".equalsIgnoreCase(country)) {
            patternsToTry = new String[] {
                "##+\\s*" + Pattern.quote(sectionName)
            };
        } else {
            patternsToTry = new String[] {
                "##+\\s*" + Pattern.quote(sectionName),
                "##+\\s*[^\\n]*\\(" + Pattern.quote(country) + "\\)[^\\n]*" + Pattern.quote(sectionName),
                "##+\\s*[^\\n]*\\(" + Pattern.quote(country) + "\\)"
            };
        }
        
        for (String patternStr : patternsToTry) {
            Pattern sectionPattern = Pattern.compile("(" + patternStr + ".*?)(\n## |\\Z)", Pattern.DOTALL);
            Matcher matcher = sectionPattern.matcher(content);
            
            if (matcher.find()) {
                foundSectionHeader = matcher.group(1);
                foundSectionStart = matcher.start(1);
                foundSectionEnd = matcher.end(1);
                break;
            }
        }
        
        if (foundSectionHeader != null) {
            // 找到现有章节，更新代码块
            int codeBlockStart = foundSectionHeader.indexOf("```");
            String newSection;
            
            if (codeBlockStart != -1) {
                // 找到代码块的结束位置
                int codeBlockEnd = foundSectionHeader.indexOf("```", codeBlockStart + 3);
                if (codeBlockEnd != -1) {
                    // 替换整个代码块内容
                    newSection = foundSectionHeader.substring(0, codeBlockStart) + "```markdown\n" + newContent + "\n```";
                } else {
                    // 代码块没有结束，追加内容
                    newSection = foundSectionHeader + "\n" + newContent + "\n```";
                }
            } else {
                // 没有代码块，添加代码块
                newSection = foundSectionHeader + "\n```markdown\n" + newContent + "\n```";
            }
            
            return content.substring(0, foundSectionStart) + newSection + content.substring(foundSectionEnd);
        }
        
        // 如果没找到，添加新的章节
        if (!content.trim().endsWith("\n")) {
            content += "\n";
        }
        if ("default".equalsIgnoreCase(country)) {
            content += "\n## " + sectionName + "\n\n```markdown\n" + newContent + "\n```\n";
        } else {
            // 对于新国家，使用带括号的格式，和 feishu.md 保持一致
            content += "\n### " + getCountryName(country) + " (" + country + ") - " + sectionName + "\n\n```markdown\n" + newContent + "\n```\n";
        }
        return content;
    }
    
    private String getCountryName(String countryCode) {
        switch (countryCode.toUpperCase()) {
            case "CN": return "中国";
            case "FR": return "法国";
            case "DE": return "德国";
            case "PL": return "波兰";
            case "NL": return "荷兰";
            case "CZ": return "捷克";
            default: return countryCode;
        }
    }
}
