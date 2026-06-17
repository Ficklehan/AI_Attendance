package com.attendance.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 canonical feishu.md 解析各国 Bitable 配置（仅用于数据库播种，运行时不再读 md）。
 */
public final class FeishuCanonicalParser {

    private static final Pattern SECTION_HEADER = Pattern.compile("^##+\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern COUNTRY_CODE = Pattern.compile("\\(([A-Z]{2})\\)");

    private FeishuCanonicalParser() {
    }

    public static final class ParsedFeishu {
        public String countryCode;
        public String appToken;
        public String tableId;
        public List<Map<String, Object>> fieldMapping = new ArrayList<>();
        public boolean syncEnabled = true;

        public boolean isValid() {
            return countryCode != null && !countryCode.isEmpty() && fieldMapping != null && !fieldMapping.isEmpty();
        }
    }

    public static Map<String, ParsedFeishu> parse(String markdown) {
        Map<String, ParsedFeishu> result = new LinkedHashMap<>();
        if (markdown == null || markdown.trim().isEmpty()) {
            return result;
        }

        Matcher headerMatcher = SECTION_HEADER.matcher(markdown);
        List<int[]> sections = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (headerMatcher.find()) {
            sections.add(new int[] { headerMatcher.start(), headerMatcher.end() });
            titles.add(headerMatcher.group(1).trim());
        }

        for (int i = 0; i < sections.size(); i++) {
            int bodyStart = sections.get(i)[1];
            int bodyEnd = i + 1 < sections.size() ? sections.get(i + 1)[0] : markdown.length();
            String title = titles.get(i);
            String body = markdown.substring(bodyStart, bodyEnd);
            String yaml = extractYamlBlock(body);
            if (yaml == null || yaml.trim().isEmpty()) {
                continue;
            }

            String countryCode = resolveCountryCode(title);
            if (countryCode == null) {
                continue;
            }

            ParsedFeishu parsed = new ParsedFeishu();
            parsed.countryCode = countryCode;
            parsed.appToken = nullToEmpty(extractYamlScalar(yaml, "bitable_app_token"));
            parsed.tableId = nullToEmpty(extractYamlScalar(yaml, "bitable_table_id"));
            parsed.fieldMapping = extractFieldMapping(yaml);
            if (parsed.isValid()) {
                result.put(countryCode, parsed);
            }
        }
        return result;
    }

    private static String resolveCountryCode(String title) {
        if (title == null) {
            return null;
        }
        if (title.contains("全局默认")) {
            return "default";
        }
        Matcher matcher = COUNTRY_CODE.matcher(title);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractYamlBlock(String sectionBody) {
        int start = sectionBody.indexOf("```yaml");
        if (start < 0) {
            return null;
        }
        start += "```yaml".length();
        int end = sectionBody.indexOf("```", start);
        if (end < 0) {
            return sectionBody.substring(start).trim();
        }
        return sectionBody.substring(start, end).trim();
    }

    private static String extractYamlScalar(String yaml, String key) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(key) + ":\\s*['\"]?([^'\"\\n]*)['\"]?", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(yaml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    public static List<Map<String, Object>> extractFieldMapping(String yaml) {
        List<Map<String, Object>> mappings = new ArrayList<>();
        Map<String, Object> current = null;
        for (String rawLine : yaml.split("\\n")) {
            String line = rawLine.trim();
            if (line.startsWith("- aiField:")) {
                if (current != null && current.containsKey("aiField")) {
                    mappings.add(current);
                }
                current = new HashMap<>();
                current.put("aiField", unquote(line.substring("- aiField:".length()).trim()));
            } else if (current != null && line.startsWith("feishuField:")) {
                current.put("feishuField", unquote(line.substring("feishuField:".length()).trim()));
            } else if (current != null && line.startsWith("type:")) {
                current.put("type", unquote(line.substring("type:".length()).trim()));
            } else if (current != null && line.startsWith("required:")) {
                current.put("required", Boolean.parseBoolean(line.substring("required:".length()).trim()));
            } else if (current != null && line.startsWith("description:")) {
                current.put("description", unquote(line.substring("description:".length()).trim()));
            }
        }
        if (current != null && current.containsKey("aiField")) {
            mappings.add(current);
        }
        return mappings;
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^['\"]|['\"]$", "");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
