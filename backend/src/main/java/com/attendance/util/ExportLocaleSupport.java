package com.attendance.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Excel 导出表头与固定文案多语言（与 PC 端 locale 对齐）。
 */
public final class ExportLocaleSupport {

    public static final String DEFAULT_LOCALE = "zh-CN";
    public static final String FALLBACK_LOCALE = "en-US";

    private static final JSONObject ROOT = loadRoot();
    private static final JSONObject LABELS = ROOT.getJSONObject("labels");

    private ExportLocaleSupport() {
    }

    private static JSONObject loadRoot() {
        try {
            InputStream in = ExportLocaleSupport.class.getResourceAsStream("/export-labels.json");
            if (in == null) {
                return new JSONObject();
            }
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String text = scanner.hasNext() ? scanner.next() : "{}";
            scanner.close();
            in.close();
            return JSON.parseObject(text);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static String resolveLocale(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            return defaultLocale();
        }
        String normalized = requested.trim().replace('_', '-');
        if (LABELS != null && LABELS.containsKey(normalized)) {
            return normalized;
        }
        int dash = normalized.indexOf('-');
        if (dash > 0) {
            String lang = normalized.substring(0, dash);
            for (String key : LABELS.keySet()) {
                if (key.startsWith(lang + "-")) {
                    return key;
                }
            }
        }
        return fallbackLocale();
    }

    public static String defaultLocale() {
        String configured = ROOT.getString("defaultLocale");
        return configured != null && !configured.trim().isEmpty() ? configured.trim() : DEFAULT_LOCALE;
    }

    public static String fallbackLocale() {
        String configured = ROOT.getString("fallbackLocale");
        return configured != null && !configured.trim().isEmpty() ? configured.trim() : FALLBACK_LOCALE;
    }

    public static String text(String locale, String key) {
        String resolved = resolveLocale(locale);
        String value = readText(resolved, key);
        if (value != null) {
            return value;
        }
        if (!fallbackLocale().equals(resolved)) {
            value = readText(fallbackLocale(), key);
            if (value != null) {
                return value;
            }
        }
        return readText(defaultLocale(), key) != null ? readText(defaultLocale(), key) : key;
    }

    public static String[] headers(String locale, String key) {
        List<String> list = readArray(resolveLocale(locale), key);
        if (list.isEmpty() && !fallbackLocale().equals(resolveLocale(locale))) {
            list = readArray(fallbackLocale(), key);
        }
        if (list.isEmpty()) {
            list = readArray(defaultLocale(), key);
        }
        return list.toArray(new String[0]);
    }

    public static String formatTaskStatus(String locale, String status) {
        if (status == null || status.trim().isEmpty()) {
            return "";
        }
        String key = "status." + status.trim().toLowerCase(Locale.ROOT);
        String translated = text(locale, key);
        return key.equals(translated) ? status : translated;
    }

    public static String dayHeader(String locale, int dayOfWeekIso, String dateSuffix) {
        List<String> days = readArray(resolveLocale(locale), "dayHeaders");
        if (days.isEmpty()) {
            days = readArray(fallbackLocale(), "dayHeaders");
        }
        int index = Math.max(1, Math.min(7, dayOfWeekIso)) - 1;
        String dow = index < days.size() ? days.get(index) : "";
        if (dateSuffix == null || dateSuffix.isEmpty()) {
            return dow;
        }
        return dow.isEmpty() ? dateSuffix : dow + " " + dateSuffix;
    }

    private static String readText(String locale, String key) {
        JSONObject bucket = LABELS != null ? LABELS.getJSONObject(locale) : null;
        if (bucket == null) {
            return null;
        }
        String value = bucket.getString(key);
        return value != null && !value.trim().isEmpty() ? value : null;
    }

    private static List<String> readArray(String locale, String key) {
        JSONObject bucket = LABELS != null ? LABELS.getJSONObject(locale) : null;
        if (bucket == null) {
            return Collections.emptyList();
        }
        JSONArray arr = bucket.getJSONArray(key);
        if (arr == null || arr.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            String item = arr.getString(i);
            out.add(item != null ? item : "");
        }
        return out;
    }
}
