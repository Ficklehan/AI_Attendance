package com.attendance.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从内置 canonical/prompts.md 解析各国提示词（仅用于播种，运行时以 DB 为准）。
 */
public final class PromptCanonicalParser {

    private static final String DEFAULT_CONTINUE = "请接续上文继续输出，不要重复已有内容，保持相同格式。";

    private PromptCanonicalParser() {
    }

    public static Map<String, ParsedPrompt> parse(String markdown) {
        Map<String, ParsedPrompt> result = new LinkedHashMap<>();
        if (markdown == null || markdown.trim().isEmpty()) {
            return result;
        }

        String defaultAi = extractSection(markdown, "主要识别提示词", "```markdown", "```");
        String defaultContinue = extractSection(markdown, "继续输出提示词", "```markdown", "```");
        if (defaultAi != null && !defaultAi.trim().isEmpty()) {
            result.put("default", new ParsedPrompt(defaultAi.trim(),
                    fallbackContinue(defaultContinue)));
        }

        Pattern countryPattern = Pattern.compile(
                "##+\\s*[^\\n]*\\(([A-Z]{2})\\)[^\\n]*?(识别提示词|继续提示词)[^\\n]*\\n(.*?)```markdown(.*?)```",
                Pattern.DOTALL);
        Matcher matcher = countryPattern.matcher(markdown);
        while (matcher.find()) {
            String code = matcher.group(1);
            String kind = matcher.group(2);
            String body = matcher.group(4).trim();
            ParsedPrompt existing = result.get(code);
            if (existing == null) {
                existing = new ParsedPrompt("", DEFAULT_CONTINUE);
                result.put(code, existing);
            }
            if (kind.contains("识别")) {
                existing.aiPrompt = body;
            } else {
                existing.continuePrompt = body;
            }
        }

        for (Map.Entry<String, ParsedPrompt> entry : result.entrySet()) {
            ParsedPrompt p = entry.getValue();
            if (p.continuePrompt == null || p.continuePrompt.trim().isEmpty()) {
                p.continuePrompt = DEFAULT_CONTINUE;
            }
        }
        return result;
    }

    private static String fallbackContinue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_CONTINUE;
        }
        return value.trim();
    }

    private static String extractSection(String content, String sectionName, String codeStart, String codeEnd) {
        String regex = "(?:##+\\s*)" + Pattern.quote(sectionName) + "(.*?)(?=##|$)";
        Pattern sectionPattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher sectionMatcher = sectionPattern.matcher(content);
        if (!sectionMatcher.find()) {
            return null;
        }
        String sectionContent = sectionMatcher.group(1);
        int startIdx = sectionContent.indexOf(codeStart);
        if (startIdx == -1) {
            return sectionContent.trim();
        }
        startIdx += codeStart.length();
        int endIdx = sectionContent.indexOf(codeEnd, startIdx);
        if (endIdx == -1) {
            return sectionContent.substring(startIdx).trim();
        }
        return sectionContent.substring(startIdx, endIdx).trim();
    }

    public static final class ParsedPrompt {
        public String aiPrompt;
        public String continuePrompt;

        public ParsedPrompt(String aiPrompt, String continuePrompt) {
            this.aiPrompt = aiPrompt;
            this.continuePrompt = continuePrompt;
        }

        public boolean isValid() {
            return aiPrompt != null && !aiPrompt.trim().isEmpty();
        }
    }
}
