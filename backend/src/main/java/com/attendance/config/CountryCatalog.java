package com.attendance.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 PC 端 Config.vue 保持一致的国家列表（单一数据源）。
 */
public final class CountryCatalog {

    private CountryCatalog() {
    }

    public static final List<Map<String, String>> OPTIONS = buildOptions();

    private static final Map<String, String> PAYS_LABELS = buildPaysLabels();

    private static Map<String, String> buildPaysLabels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("CN", "China");
        map.put("FR", "France");
        map.put("DE", "Germany");
        map.put("US", "United States");
        map.put("PL", "Poland");
        map.put("NL", "Netherlands");
        map.put("IT", "Italy");
        map.put("ES", "Spain");
        map.put("CZ", "Czech Republic");
        return map;
    }

    private static List<Map<String, String>> buildOptions() {
        List<Map<String, String>> list = new ArrayList<>();
        add(list, "default", "🇺🇳", "全局默认");
        add(list, "CN", "🇨🇳", "中国");
        add(list, "FR", "🇫🇷", "法国");
        add(list, "DE", "🇩🇪", "德国");
        add(list, "US", "🇺🇸", "美国");
        add(list, "PL", "🇵🇱", "波兰");
        add(list, "NL", "🇳🇱", "荷兰");
        add(list, "IT", "🇮🇹", "意大利");
        add(list, "ES", "🇪🇸", "西班牙");
        add(list, "CZ", "🇨🇿", "捷克");
        return list;
    }

    private static void add(List<Map<String, String>> list, String code, String flag, String name) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("flag", flag);
        item.put("name", name);
        list.add(item);
    }

    public static boolean isSupported(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String normalized = code.trim().toUpperCase();
        return OPTIONS.stream().anyMatch(o -> o.get("code").equalsIgnoreCase(normalized));
    }

    /** 考勤表 Pays 字段缺省值（与识别结果、飞书字段常用英文国名对齐）。 */
    public static String defaultPaysLabel(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty() || "default".equalsIgnoreCase(countryCode.trim())) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase();
        String mapped = PAYS_LABELS.get(normalized);
        if (mapped != null && !mapped.trim().isEmpty()) {
            return mapped;
        }
        return OPTIONS.stream()
                .filter(o -> normalized.equalsIgnoreCase(o.get("code")))
                .map(o -> o.get("name"))
                .findFirst()
                .orElse(normalized);
    }
}
