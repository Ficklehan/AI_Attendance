package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 提醒文案多语言：按任务 prompt_country 映射 locale，并提供默认模板与标签翻译。
 */
public final class ReminderLocaleSupport {

    public static final String DEFAULT_LOCALE = "zh-CN";
    public static final String FALLBACK_LOCALE = "en-US";

    private static final JSONObject ROOT = loadRoot();
    private static final Map<String, String> COUNTRY_LOCALE = parseCountryLocale();
    private static final List<String> SUPPORTED_LOCALES = parseSupportedLocales();

    private ReminderLocaleSupport() {
    }

    private static JSONObject loadRoot() {
        try {
            ClassPathResource resource = new ClassPathResource("reminder/default-templates.json");
            InputStream in = resource.getInputStream();
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String text = scanner.hasNext() ? scanner.next() : "{}";
            scanner.close();
            in.close();
            return JSON.parseObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static Map<String, String> parseCountryLocale() {
        JSONObject obj = ROOT.getJSONObject("countryLocale");
        Map<String, String> map = new LinkedHashMap<>();
        if (obj != null) {
            for (String key : obj.keySet()) {
                map.put(normalizeCountry(key), obj.getString(key));
            }
        }
        if (!map.containsKey("default")) {
            map.put("default", DEFAULT_LOCALE);
        }
        return Collections.unmodifiableMap(map);
    }

    private static List<String> parseSupportedLocales() {
        JSONObject operator = ROOT.getJSONObject("operator");
        List<String> locales = new ArrayList<>();
        if (operator != null) {
            for (String key : operator.keySet()) {
                locales.add(key);
            }
        }
        if (locales.isEmpty()) {
            locales.add(DEFAULT_LOCALE);
            locales.add(FALLBACK_LOCALE);
        }
        return Collections.unmodifiableList(locales);
    }

    public static List<String> supportedLocales() {
        return SUPPORTED_LOCALES;
    }

    public static Map<String, String> countryLocaleMap() {
        return COUNTRY_LOCALE;
    }

    public static String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return "default";
        }
        return countryCode.trim().toUpperCase();
    }

    public static String resolveLocale(String promptCountry) {
        String country = normalizeCountry(promptCountry);
        String locale = COUNTRY_LOCALE.get(country);
        if (locale == null || locale.trim().isEmpty()) {
            locale = COUNTRY_LOCALE.get("default");
        }
        if (locale == null || locale.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        return locale;
    }

    public static String defaultOperatorTemplate(String locale) {
        return pickFromSection("operator", locale);
    }

    public static String defaultSupervisorTemplate(String locale) {
        return pickFromSection("supervisor", locale);
    }

    public static Map<String, String> allDefaultOperatorTemplates() {
        return copySection("operator");
    }

    public static Map<String, String> allDefaultSupervisorTemplates() {
        return copySection("supervisor");
    }

    public static String formatStatusLabel(String status, String locale) {
        if (status == null) {
            return "";
        }
        JSONObject labels = sectionForLocale("status", locale);
        if (labels != null && labels.containsKey(status)) {
            return labels.getString(status);
        }
        return ReminderSupport.formatStatusLabel(status);
    }

    public static String formatThreshold(java.math.BigDecimal value, String unit, String locale) {
        String display = ReminderSupport.formatIntervalDisplay(value);
        JSONObject units = sectionForLocale("unit", locale);
        String normalized = ReminderSupport.normalizeIntervalUnit(unit);
        if (units != null && units.containsKey(normalized)) {
            return display + " " + units.getString(normalized);
        }
        return ReminderSupport.formatThreshold(value, unit);
    }

    public static String notificationTitlePrefix(String locale) {
        return pickFromSection("notificationTitlePrefix", locale);
    }

    public static String viewTaskLabel(String locale) {
        return pickFromSection("viewTask", locale);
    }

    public static String aggregatedNotificationTitle(String locale, int ruleCount) {
        String resolved = resolveLocaleKey(locale);
        if ("zh-CN".equals(resolved)) {
            return ruleCount > 1 ? "【考勤任务提醒】" : "【考勤提醒】";
        }
        return ruleCount > 1 ? "Attendance reminders" : "Attendance reminder";
    }

    public static String taskCountSuffix(String locale) {
        return "zh-CN".equals(resolveLocaleKey(locale)) ? " 个任务" : " tasks";
    }

    public static String buildAggregatedBody(String locale,
                                             String recipientName,
                                             boolean recipientIsOperator,
                                             int totalTaskCount,
                                             int ruleCount,
                                             String ruleSummaryLines,
                                             String latestTaskId,
                                             LocalDateTime latestTime,
                                             String creatorNames) {
        String resolved = resolveLocaleKey(locale);
        String timeText = latestTime != null
                ? latestTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "-";
        if ("zh-CN".equals(resolved)) {
            if (recipientIsOperator) {
                return "【考勤待核对提醒】\n\n"
                        + "您有 " + totalTaskCount + " 个任务需要处理"
                        + (ruleCount > 1 ? "（涉及 " + ruleCount + " 条提醒规则）" : "")
                        + "：\n\n"
                        + ruleSummaryLines + "\n\n"
                        + "最近任务：" + latestTaskId + "\n"
                        + "进入状态：" + timeText + "\n\n"
                        + "请登录系统及时完成处理。";
            }
            return "【考勤督办提醒】\n\n"
                    + recipientName + "，您好\n\n"
                    + "共有 " + totalTaskCount + " 个任务需要处理"
                    + (ruleCount > 1 ? "（涉及 " + ruleCount + " 条提醒规则）" : "")
                    + "：\n\n"
                    + ruleSummaryLines + "\n"
                    + "涉及操作者：" + creatorNames + "\n\n"
                    + "最近任务：" + latestTaskId + "\n"
                    + "进入状态：" + timeText + "\n\n"
                    + "请关注并督促相关人员登录系统完成处理。";
        }
        return "You have " + totalTaskCount + " task(s) pending"
                + (ruleCount > 1 ? " across " + ruleCount + " reminder rules" : "")
                + ":\n\n"
                + ruleSummaryLines + "\n\n"
                + "Latest task: " + latestTaskId + "\n"
                + "Status since: " + timeText + "\n\n"
                + "Please sign in and complete the review.";
    }

    public static String nameSeparator(String locale) {
        String sep = pickFromSection("nameSeparator", locale);
        return sep != null && !sep.isEmpty() ? sep : "、";
    }

    /**
     * 按界面语言展示时使用：仅匹配目标 locale，否则用内置默认模板，避免回退到中文自定义文案。
     */
    public static String pickTemplateForDisplay(Map<String, String> templates, String locale, boolean operator) {
        String resolved = normalizeLocale(locale);
        if (templates != null && !templates.isEmpty()) {
            String exact = templates.get(resolved);
            if (exact != null && !exact.trim().isEmpty()) {
                return exact.trim();
            }
        }
        return operator ? defaultOperatorTemplate(resolved) : defaultSupervisorTemplate(resolved);
    }

    public static String pickLocalizedTemplate(Map<String, String> operatorLocales,
                                               Map<String, String> supervisorLocales,
                                               String legacyOperator,
                                               String legacySupervisor,
                                               String locale,
                                               boolean recipientIsTaskOperator) {
        Map<String, String> templates = recipientIsTaskOperator ? operatorLocales : supervisorLocales;
        String legacy = recipientIsTaskOperator ? legacyOperator : legacySupervisor;
        boolean operator = recipientIsTaskOperator;
        String picked = pickFromMap(templates, locale);
        if (picked != null) {
            return picked;
        }
        if (legacy != null && !legacy.trim().isEmpty()) {
            return legacy;
        }
        return operator ? defaultOperatorTemplate(locale) : defaultSupervisorTemplate(locale);
    }

    public static Map<String, String> parseTemplateMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            Map<String, String> map = new LinkedHashMap<>();
            if (obj != null) {
                for (String key : obj.keySet()) {
                    String value = obj.getString(key);
                    if (value != null && !value.trim().isEmpty()) {
                        map.put(key, value);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static String toTemplateJson(Map<String, String> templates) {
        if (templates == null || templates.isEmpty()) {
            return null;
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String value = entry.getValue().trim();
            if (!value.isEmpty()) {
                cleaned.put(entry.getKey().trim(), value);
            }
        }
        return cleaned.isEmpty() ? null : JSON.toJSONString(cleaned);
    }

    public static String primaryTemplateForStorage(Map<String, String> templates) {
        return primaryTemplateForStorage(templates, true);
    }

    public static String primarySupervisorTemplateForStorage(Map<String, String> templates) {
        return primaryTemplateForStorage(templates, false);
    }

    private static String primaryTemplateForStorage(Map<String, String> templates, boolean operator) {
        String zh = pickFromMap(templates, DEFAULT_LOCALE);
        if (zh != null) {
            return zh;
        }
        for (String locale : SUPPORTED_LOCALES) {
            String value = pickFromMap(templates, locale);
            if (value != null) {
                return value;
            }
        }
        if (templates != null) {
            for (String value : templates.values()) {
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        return operator ? defaultOperatorTemplate(DEFAULT_LOCALE) : defaultSupervisorTemplate(DEFAULT_LOCALE);
    }

    private static String pickFromMap(Map<String, String> map, String locale) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String resolved = resolveLocaleKey(locale);
        if (map.containsKey(resolved)) {
            String value = map.get(resolved);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        if (map.containsKey(DEFAULT_LOCALE)) {
            String value = map.get(DEFAULT_LOCALE);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        if (map.containsKey(FALLBACK_LOCALE)) {
            String value = map.get(FALLBACK_LOCALE);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        for (String value : map.values()) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Map<String, String> copySection(String section) {
        JSONObject obj = ROOT.getJSONObject(section);
        Map<String, String> map = new LinkedHashMap<>();
        if (obj != null) {
            for (String key : obj.keySet()) {
                map.put(key, obj.getString(key));
            }
        }
        return map;
    }

    private static String pickFromSection(String section, String locale) {
        JSONObject obj = ROOT.getJSONObject(section);
        if (obj == null) {
            return "";
        }
        String resolved = resolveLocaleKey(locale);
        if (obj.containsKey(resolved)) {
            return obj.getString(resolved);
        }
        if (obj.containsKey(DEFAULT_LOCALE)) {
            return obj.getString(DEFAULT_LOCALE);
        }
        if (obj.containsKey(FALLBACK_LOCALE)) {
            return obj.getString(FALLBACK_LOCALE);
        }
        return "";
    }

    private static JSONObject sectionForLocale(String section, String locale) {
        JSONObject root = ROOT.getJSONObject(section);
        if (root == null) {
            return null;
        }
        String resolved = resolveLocaleKey(locale);
        JSONObject localized = root.getJSONObject(resolved);
        if (localized != null) {
            return localized;
        }
        return root.getJSONObject(DEFAULT_LOCALE);
    }

    public static String normalizeLocale(String locale) {
        return resolveLocaleKey(locale);
    }

    private static String resolveLocaleKey(String locale) {
        if (locale == null || locale.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        String trimmed = locale.trim();
        if (SUPPORTED_LOCALES.contains(trimmed)) {
            return trimmed;
        }
        return DEFAULT_LOCALE;
    }
}
