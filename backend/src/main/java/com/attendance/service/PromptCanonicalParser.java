package com.attendance.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从内置 canonical/prompts.md 解析各国提示词（仅用于播种，运行时以 DB 为准）。
 * 文件级标题：{@code ## 主要识别提示词} / {@code ## 继续输出提示词} / {@code ## 法国 (FR) - 识别提示词}。
 * 正文里的 {@code ## 1.} 或内嵌 {@code ```json} 不算文件级标题，不会截断。
 */
public final class PromptCanonicalParser {

    private static final String DEFAULT_CONTINUE = "请接续上文继续输出，不要重复已有内容，保持相同格式。";

    /** 文件级章节标题：default 两节 + 各国识别/继续。 */
    private static final Pattern FILE_HEADING = Pattern.compile(
            "(?m)^##\\s+(?:主要识别提示词|继续输出提示词|.+\\([A-Z]{2}\\).+(?:识别提示词|继续提示词))\\s*$");

    private static final Pattern COUNTRY_HEADING = Pattern.compile(
            "(?m)^##+\\s*[^\\n]*\\(([A-Z]{2})\\)[^\\n]*?(识别提示词|继续提示词)[^\\n]*$");

    private PromptCanonicalParser() {
    }

    public static Map<String, ParsedPrompt> parse(String markdown) {
        Map<String, ParsedPrompt> result = new LinkedHashMap<>();
        if (markdown == null || markdown.trim().isEmpty()) {
            return result;
        }

        String defaultAi = extractNamedSection(markdown, "主要识别提示词");
        String defaultContinue = extractNamedSection(markdown, "继续输出提示词");
        if (defaultAi != null && !defaultAi.trim().isEmpty()) {
            result.put("default", new ParsedPrompt(defaultAi.trim(),
                    fallbackContinue(defaultContinue)));
        }

        Matcher matcher = COUNTRY_HEADING.matcher(markdown);
        while (matcher.find()) {
            String code = matcher.group(1);
            String kind = matcher.group(2);
            String body = extractFenceFromRange(markdown, matcher.end(), nextFileHeadingStart(markdown, matcher.end()));
            if (body == null || body.isEmpty()) {
                continue;
            }
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

    private static String extractNamedSection(String markdown, String sectionName) {
        Pattern heading = Pattern.compile("(?m)^##+\\s*" + Pattern.quote(sectionName) + "\\s*$");
        Matcher hm = heading.matcher(markdown);
        if (!hm.find()) {
            return null;
        }
        return extractFenceFromRange(markdown, hm.end(), nextFileHeadingStart(markdown, hm.end()));
    }

    private static int nextFileHeadingStart(String markdown, int from) {
        Matcher next = FILE_HEADING.matcher(markdown);
        if (next.find(from)) {
            return next.start();
        }
        return markdown.length();
    }

    /**
     * 取区间内 {@code ```markdown} 到该区间最后一个行首 {@code ```} 之间的正文。
     * 这样正文里的 {@code ```json} 示例不会提前截断。
     */
    private static String extractFenceFromRange(String markdown, int from, int to) {
        if (from < 0 || to <= from || from >= markdown.length()) {
            return null;
        }
        int end = Math.min(to, markdown.length());
        String section = markdown.substring(from, end);
        int fenceTag = indexOfFenceTag(section, "```markdown");
        if (fenceTag < 0) {
            return section.trim();
        }
        int contentStart = skipFenceLine(section, fenceTag + "```markdown".length());
        int close = lastLineStartFence(section, contentStart);
        if (close < 0) {
            return section.substring(contentStart).trim();
        }
        return section.substring(contentStart, close).trim();
    }

    private static int indexOfFenceTag(String text, String tag) {
        int idx = 0;
        while (true) {
            int found = text.indexOf(tag, idx);
            if (found < 0) {
                return -1;
            }
            if (found == 0 || text.charAt(found - 1) == '\n') {
                return found;
            }
            idx = found + tag.length();
        }
    }

    private static int skipFenceLine(String text, int afterTag) {
        int i = afterTag;
        if (i < text.length() && text.charAt(i) == '\r') {
            i++;
        }
        if (i < text.length() && text.charAt(i) == '\n') {
            i++;
        }
        return i;
    }

    private static int lastLineStartFence(String text, int from) {
        int last = -1;
        int idx = from;
        while (idx < text.length()) {
            int found = text.indexOf("```", idx);
            if (found < 0) {
                break;
            }
            if (found == 0 || text.charAt(found - 1) == '\n') {
                last = found;
            }
            idx = found + 3;
        }
        return last;
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
