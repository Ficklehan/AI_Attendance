package com.attendance.service;

import com.attendance.config.PromptProperties;
import com.attendance.entity.RecognitionPrompt;
import com.attendance.mapper.RecognitionPromptMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecognitionPromptService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionPromptService.class);

    private final ConcurrentHashMap<String, String> aiPromptCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> continuePromptCache = new ConcurrentHashMap<>();

    @Autowired
    private RecognitionPromptMapper recognitionPromptMapper;

    @Autowired
    private PromptProperties promptProperties;

    public String getAiPrompt(String country) {
        String code = normalizeCountry(country);
        String effective = resolveEffectivePromptCountry(code);
        return aiPromptCache.computeIfAbsent(effective, this::loadAiPromptFromDb);
    }

    public String getContinuePrompt(String country) {
        String code = normalizeCountry(country);
        String effective = resolveEffectivePromptCountry(code);
        return continuePromptCache.computeIfAbsent(effective, this::loadContinuePromptFromDb);
    }

    private String loadAiPromptFromDb(String effective) {
        RecognitionPrompt row = recognitionPromptMapper.selectByCountry(effective);
        if (row == null || row.getAiPrompt() == null || row.getAiPrompt().trim().isEmpty()) {
            row = recognitionPromptMapper.selectByCountry("default");
        }
        return row != null ? row.getAiPrompt() : null;
    }

    private String loadContinuePromptFromDb(String effective) {
        RecognitionPrompt row = recognitionPromptMapper.selectByCountry(effective);
        if (row == null || row.getContinuePrompt() == null || row.getContinuePrompt().trim().isEmpty()) {
            row = recognitionPromptMapper.selectByCountry("default");
        }
        return row != null ? row.getContinuePrompt() : null;
    }

    private void clearPromptCache() {
        aiPromptCache.clear();
        continuePromptCache.clear();
    }

    public boolean hasCountryPrompt(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return true;
        }
        RecognitionPrompt row = recognitionPromptMapper.selectByCountry(country.trim().toUpperCase());
        return row != null && row.getAiPrompt() != null && !row.getAiPrompt().trim().isEmpty();
    }

    public String resolveEffectivePromptCountry(String country) {
        if (country == null || country.trim().isEmpty() || "default".equalsIgnoreCase(country.trim())) {
            return "default";
        }
        String normalized = country.trim().toUpperCase();
        if (hasCountryPrompt(normalized)) {
            return normalized;
        }
        return "default";
    }

    public String describePromptSection(String country) {
        String effective = resolveEffectivePromptCountry(country);
        if ("default".equalsIgnoreCase(effective)) {
            return "主要识别提示词（全局 default，数据库）";
        }
        return "国家配置 " + effective + " - 识别提示词（数据库）";
    }

    public void saveUserPrompt(String country, String aiPrompt, String continuePrompt) {
        RecognitionPrompt row = new RecognitionPrompt();
        row.setCountryCode(normalizeCountry(country));
        row.setAiPrompt(aiPrompt);
        row.setContinuePrompt(continuePrompt != null ? continuePrompt : "");
        row.setSeedVersion(promptProperties.getSeedVersion());
        row.setUserModified(true);
        recognitionPromptMapper.upsertUserEdit(row);
        clearPromptCache();
        log.info("已保存用户提示词: country={}", row.getCountryCode());
    }

    public boolean isLegacyPromptInDatabase() {
        RecognitionPrompt def = recognitionPromptMapper.selectByCountry("default");
        if (def == null || def.getAiPrompt() == null) {
            return true;
        }
        String prompt = def.getAiPrompt();
        if (MarkdownConfigService.isLegacyPromptsFile(prompt)) {
            return true;
        }
        return prompt.contains("规则：") && prompt.contains("1. 只返回真实数据");
    }

    public boolean isOutdatedSeedInDatabase() {
        RecognitionPrompt def = recognitionPromptMapper.selectByCountry("default");
        if (def == null) {
            return true;
        }
        return def.getSeedVersion() < promptProperties.getSeedVersion();
    }

    public boolean isMissingPageNumPromptInDatabase() {
        RecognitionPrompt def = recognitionPromptMapper.selectByCountry("default");
        if (def == null || def.getAiPrompt() == null) {
            return true;
        }
        String prompt = def.getAiPrompt();
        return !prompt.contains("PAGE_NUM");
    }

    public long countRows() {
        return recognitionPromptMapper.countAll();
    }

    /**
     * 从内置 canonical/prompts.md 播种到数据库。
     *
     * @param force true=覆盖全部（初始化场景）；false=仅补全/升级未自定义行
     */
    public int seedFromCanonical(boolean force) {
        String markdown = readCanonicalResource();
        if (markdown == null || markdown.trim().isEmpty()) {
            log.error("内置 canonical/prompts.md 为空，无法播种");
            return 0;
        }
        Map<String, PromptCanonicalParser.ParsedPrompt> parsed = PromptCanonicalParser.parse(markdown);
        int version = promptProperties.getSeedVersion();
        int count = 0;
        for (Map.Entry<String, PromptCanonicalParser.ParsedPrompt> entry : parsed.entrySet()) {
            PromptCanonicalParser.ParsedPrompt p = entry.getValue();
            if (!p.isValid()) {
                continue;
            }
            RecognitionPrompt row = new RecognitionPrompt();
            row.setCountryCode(entry.getKey());
            row.setAiPrompt(p.aiPrompt);
            row.setContinuePrompt(p.continuePrompt);
            row.setSeedVersion(version);
            row.setUserModified(false);
            if (force) {
                recognitionPromptMapper.upsertForceSeed(row);
            } else {
                recognitionPromptMapper.upsertSystemSeed(row);
            }
            count++;
        }
        log.info("识别提示词播种完成: countries={}, force={}, seedVersion={}", count, force, version);
        clearPromptCache();
        return count;
    }

    public int seedFromMarkdownContent(String markdown, boolean force) {
        Map<String, PromptCanonicalParser.ParsedPrompt> parsed = PromptCanonicalParser.parse(markdown);
        int version = promptProperties.getSeedVersion();
        int count = 0;
        for (Map.Entry<String, PromptCanonicalParser.ParsedPrompt> entry : parsed.entrySet()) {
            PromptCanonicalParser.ParsedPrompt p = entry.getValue();
            if (!p.isValid()) {
                continue;
            }
            RecognitionPrompt row = new RecognitionPrompt();
            row.setCountryCode(entry.getKey());
            row.setAiPrompt(p.aiPrompt);
            row.setContinuePrompt(p.continuePrompt);
            row.setSeedVersion(version);
            row.setUserModified(false);
            if (force) {
                recognitionPromptMapper.upsertForceSeed(row);
            } else {
                recognitionPromptMapper.upsertSystemSeed(row);
            }
            count++;
        }
        clearPromptCache();
        return count;
    }

    public List<String> listCountryCodes() {
        return recognitionPromptMapper.selectAllCountryCodes();
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            return "default";
        }
        return "default".equalsIgnoreCase(country.trim()) ? "default" : country.trim().toUpperCase();
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private String readCanonicalResource() {
        try (InputStream in = getClass().getResourceAsStream("/canonical/prompts.md")) {
            if (in == null) {
                return null;
            }
            return new String(readAllBytes(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取 canonical/prompts.md 失败", e);
            return null;
        }
    }
}
