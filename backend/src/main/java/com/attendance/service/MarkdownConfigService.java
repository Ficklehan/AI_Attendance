package com.attendance.service;

import com.attendance.config.ConfigPathResolver;
import com.attendance.dto.CountryConfigBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(MarkdownConfigService.class);
    
    @Autowired
    private ConfigPathResolver configPathResolver;

    @Autowired
    private RecognitionPromptService recognitionPromptService;
    
    private String promptsContent;
    private String feishuContent;
    private String countriesContent;
    
    private String currentCountry = "default";
    private long promptsFileLastModified = -1L;
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
            
            refreshPromptsFromDisk(true);
            feishuContent = readFile("feishu.md");
            feishuFileLastModified = lastModified("feishu.md");
            countriesContent = readFile("countries.md");
            
            log.info("prompts.md 长度: {}", promptsContent.length());
            log.info("feishu.md 长度: {}", feishuContent.length());
            log.info("countries.md 长度: {}", countriesContent.length());
            log.info("配置文件加载成功");
        } catch (IOException e) {
            log.error("加载配置文件失败", e);
        }
    }

    /**
     * 读取 prompts.md；若仍为旧版 11 字段结构则自动迁移为内置 canonical 版本。
     */
    public boolean refreshPromptsFromDisk(boolean allowMigrate) throws IOException {
        Path path = configPathResolver.resolveFile("prompts.md");
        long mtime = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        if (mtime == promptsFileLastModified && promptsContent != null && !promptsContent.isEmpty()) {
            return false;
        }
        String loaded = Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : "";
        if (allowMigrate && isLegacyPromptsFile(loaded)) {
            String canonical = readCanonicalPromptsResource();
            if (canonical != null && !canonical.trim().isEmpty()) {
                log.warn("检测到旧版 prompts.md（缺少 Pays/SIGNATURE 等新字段），正在自动迁移");
                Files.write(path, canonical.getBytes(StandardCharsets.UTF_8));
                loaded = canonical;
                mtime = Files.getLastModifiedTime(path).toMillis();
            }
        }
        promptsContent = loaded;
        promptsFileLastModified = mtime;
        return true;
    }

    private void ensurePromptsFresh() {
        try {
            refreshPromptsFromDisk(true);
        } catch (IOException e) {
            log.warn("刷新 prompts.md 失败，继续使用内存缓存", e);
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
            log.warn("刷新 feishu.md 失败", e);
        }
    }

    private long lastModified(String filename) throws IOException {
        Path path = configPathResolver.resolveFile(filename);
        return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
    }

    public static boolean isLegacyPromptsFile(String content) {
        if (content == null || content.trim().isEmpty()) {
            return true;
        }
        if (content.contains("Pays,Entrepot") || content.contains("Pays, Entrepot")) {
            if (!content.contains("PAGE_NUM")) {
                return true;
            }
            if (content.contains("【数据与格式】")) {
                return false;
            }
            if (content.contains("规则：") && content.contains("1. 只返回真实数据")) {
                return true;
            }
            if (!content.contains("页码") && !content.contains("Page ")) {
                return true;
            }
            return false;
        }
        return content.contains("检查器")
                || content.contains(",CHECKER,")
                || content.contains("[NO,姓名,中介")
                || content.contains("第10个字段");
    }

    public boolean isCurrentPromptsLegacy() {
        return recognitionPromptService.isLegacyPromptInDatabase();
    }

    private String readCanonicalPromptsResource() {
        try (InputStream in = getClass().getResourceAsStream("/canonical/prompts.md")) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取内置 canonical prompts 失败", e);
            return null;
        }
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
    
    public String getAiPrompt() {
        return getAiPrompt(currentCountry);
    }
    
    /**
     * 识别提示词：所选国家无独立 AI 章节时回退 default。
     */
    public String resolveEffectiveCountry(String country) {
        return resolveEffectivePromptCountry(country);
    }

    public String resolveEffectivePromptCountry(String country) {
        return recognitionPromptService.resolveEffectivePromptCountry(country);
    }

    /**
     * 飞书多维表：所选国家无独立 feishu.md 章节时回退「全局默认配置」。
     */
    public String resolveEffectiveFeishuCountry(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return "default";
        }
        String normalized = country.trim().toUpperCase();
        if (hasCountryFeishuConfig(normalized)) {
            return normalized;
        }
        log.info("国家 {} 未配置独立飞书章节，回退全局默认配置", normalized);
        return "default";
    }

    public CountryConfigBundle getCountryConfigBundle(String country) {
        ensureFeishuFresh();
        String request = normalizeCountryCode(country);
        String effectivePrompt = resolveEffectivePromptCountry(request);
        String effectiveFeishu = resolveEffectiveFeishuCountry(request);

        CountryConfigBundle bundle = new CountryConfigBundle();
        bundle.setRequestCountry(request);
        bundle.setEffectivePromptCountry(effectivePrompt);
        bundle.setEffectiveFeishuCountry(effectiveFeishu);
        bundle.setPromptFromGlobalFallback(!"default".equalsIgnoreCase(request)
                && "default".equalsIgnoreCase(effectivePrompt));
        bundle.setFeishuFromGlobalFallback(!"default".equalsIgnoreCase(request)
                && "default".equalsIgnoreCase(effectiveFeishu));
        bundle.setPromptSection(describePromptSection(request));
        bundle.setAiPrompt(getAiPrompt(request));
        bundle.setContinuePrompt(getContinuePrompt(request));

        Map<String, Object> feishu = getFeishuConfig(request);
        bundle.setAppToken((String) feishu.get("appToken"));
        bundle.setTableId((String) feishu.get("tableId"));
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

    public String describePromptSection(String country) {
        return recognitionPromptService.describePromptSection(country);
    }

    private boolean hasCountryAiPrompt(String country) {
        return recognitionPromptService.hasCountryPrompt(country);
    }

    private String extractCountryAiPromptOnly(String country) {
        String prompt = findPromptSectionByCountryCode(promptsContent, country, "识别提示词");
        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = extractSection(promptsContent, country + " - 识别提示词", "```markdown", "```");
        }
        return prompt;
    }

    private boolean hasCountryFeishuConfig(String country) {
        String yaml = extractSection(feishuContent, country, "```yaml", "```");
        if (yaml == null || yaml.trim().isEmpty()) {
            yaml = findFeishuSectionByCountryCode(feishuContent, country);
        }
        return yaml != null && !yaml.trim().isEmpty();
    }

    public String getAiPrompt(String country) {
        String effective = resolveEffectiveCountry(country);
        log.info("getAiPrompt(DB) request={}, effective={}", country, effective);
        String prompt = recognitionPromptService.getAiPrompt(country);
        if (prompt != null) {
            log.info("最终提示词长度: {}", prompt.length());
        }
        return prompt;
    }
    
    public String getContinuePrompt() {
        return getContinuePrompt(currentCountry);
    }
    
    public String getContinuePrompt(String country) {
        return recognitionPromptService.getContinuePrompt(country);
    }
    
    private String findPromptSectionByCountryCode(String content, String countryCode, String sectionType) {
        // 匹配 "## 法国 (FR) - 识别提示词" 或 "### 中国 (CN) - 识别提示词"
        Pattern pattern = Pattern.compile("##+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)[^\\n]*"
                + Pattern.quote(sectionType) + "[^\\n]*\\n(.*?)```markdown(.*?)```", Pattern.DOTALL);
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
        ensureFeishuFresh();
        Map<String, Object> config = new HashMap<>();
        String effective = resolveEffectiveFeishuCountry(country);
        log.info("getFeishuConfig request={}, effectiveFeishu={}", country, effective);

        String targetSection = "default".equalsIgnoreCase(effective) ? "全局默认配置" : effective;
        // 先尝试直接匹配国家代码
        String yamlContent = extractSection(feishuContent, targetSection, "```yaml", "```");
        
        // 如果没找到，尝试查找带括号的格式 "### 荷兰 (NL)"
        if ((yamlContent == null || yamlContent.trim().isEmpty()) && !"default".equalsIgnoreCase(effective)) {
            yamlContent = findFeishuSectionByCountryCode(feishuContent, effective);
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
        Pattern pattern = Pattern.compile("##+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)[^\\n]*\\n(.*?)```yaml(.*?)```", Pattern.DOTALL);
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
    
    public boolean hasCountryConfig(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return true;
        }
        return hasCountryFeishuConfig(country.trim().toUpperCase());
    }
    
    public List<String> getAllCountries() {
        List<String> countries = new ArrayList<>();
        countries.add("default");
        try {
            for (String code : recognitionPromptService.listCountryCodes()) {
                if (!"default".equalsIgnoreCase(code) && !countries.contains(code)) {
                    countries.add(code);
                }
            }
        } catch (Exception e) {
            log.warn("从数据库读取提示词国家列表失败", e);
        }
        Pattern pattern = Pattern.compile("##+\\s*[^\\n]*\\(([A-Z]{2})\\)");
        collectCountryCodes(feishuContent, pattern, countries);
        return countries;
    }

    private void collectCountryCodes(String content, Pattern pattern, List<String> countries) {
        if (content == null || content.isEmpty()) {
            return;
        }
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (!countries.contains(code)) {
                countries.add(code);
            }
        }
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
        recognitionPromptService.saveUserPrompt(country, aiPrompt, continuePrompt);
        log.info("提示词已写入数据库: country={}", country);
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
        
        Path path = configPathResolver.resolveFile("feishu.md");
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
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
            String countryCode = country.trim().toUpperCase();
            String sectionSuffix = sectionName.contains("继续") ? "继续提示词" : "识别提示词";
            patternsToTry = new String[] {
                "##+\\s*" + Pattern.quote(sectionName),
                "##+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)[^\\n]*"
                        + Pattern.quote(sectionSuffix),
                "##+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)[^\\n]*"
                        + Pattern.quote(sectionName),
                "##+\\s*[^\\n]*\\(" + Pattern.quote(countryCode) + "\\)"
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
            case "IT": return "意大利";
            case "CZ": return "捷克";
            default: return countryCode;
        }
    }
}
