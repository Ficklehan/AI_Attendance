package com.attendance.config;

import com.attendance.util.CountryResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 与 PC 端 Config.vue 保持一致的国家列表（单一数据源）。
 * 含历史 Pays / country_key 别名解析（如 ITALIA、CHINA → 目录代码）。
 */
public final class CountryCatalog {

    private CountryCatalog() {
    }

    public static final List<Map<String, String>> OPTIONS = buildOptions();

    /** 未指定或使用 default 时的全局默认国家（见 base-config/countries.md） */
    public static final String GLOBAL_DEFAULT_COUNTRY = "FR";

    private static final Map<String, String> PAYS_LABELS = buildPaysLabels();
    private static final Map<String, String> LEGACY_ALIASES = buildLegacyAliases();

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

    private static Map<String, String> buildLegacyAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        registerAliases(map, "CN", "CHINA", "PRC", "CHINE");
        registerAliases(map, "FR", "FRANCE", "FRANCIA");
        registerAliases(map, "DE", "GERMANY", "DEUTSCHLAND", "ALLEMAGNE");
        registerAliases(map, "US", "USA", "UNITED STATES", "UNITEDSTATES", "AMERICA");
        registerAliases(map, "PL", "POLAND", "POLSKA", "POLOGNE");
        registerAliases(map, "NL", "NETHERLANDS", "HOLLAND", "PAYS-BAS", "PAYSBAS", "NEDERLAND");
        registerAliases(map, "IT", "ITALY", "ITALIA", "ITALIE", "ITA");
        registerAliases(map, "ES", "SPAIN", "ESPANA", "ESPAÑA", "ESPAGNE");
        registerAliases(map, "CZ", "CZECH", "CZECHIA", "CZECH REPUBLIC", "CZECHREPUBLIC", "REPUBLIQUE TCHEQUE");
        return map;
    }

    private static void registerAliases(Map<String, String> map, String code, String... aliases) {
        for (String alias : aliases) {
            if (alias == null || alias.trim().isEmpty()) {
                continue;
            }
            map.put(normalizeToken(alias), code);
        }
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
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return OPTIONS.stream().anyMatch(o -> o.get("code").equalsIgnoreCase(normalized));
    }

    /**
     * 将全局配置中的 default/空值解析为实际默认国家（法国）。
     * 显式配置的国家码原样返回。
     */
    public static String resolveGlobalDefaultCountry(String code) {
        if (code == null || code.trim().isEmpty() || "default".equalsIgnoreCase(code.trim())) {
            return GLOBAL_DEFAULT_COUNTRY;
        }
        return CountryResolver.normalize(code);
    }

    /** 考勤表 Pays 字段缺省值（与识别结果、飞书字段常用英文国名对齐）。 */
    public static String defaultPaysLabel(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty() || "default".equalsIgnoreCase(countryCode.trim())) {
            return PAYS_LABELS.get(GLOBAL_DEFAULT_COUNTRY);
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
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

    /**
     * 从 Pays / country_key / prompt_country 等原始值解析目录国家代码；无法识别时返回 null。
     */
    public static String resolveCountryCodeFromPays(String pays) {
        if (pays == null || pays.trim().isEmpty()) {
            return null;
        }
        String trimmed = pays.trim();
        if ("default".equalsIgnoreCase(trimmed)) {
            return "default";
        }
        if (isSupported(trimmed)) {
            return CountryResolver.normalize(trimmed);
        }
        String legacy = LEGACY_ALIASES.get(normalizeToken(trimmed));
        if (legacy != null) {
            return legacy;
        }
        for (Map.Entry<String, String> entry : PAYS_LABELS.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(trimmed)) {
                return entry.getKey();
            }
        }
        for (Map<String, String> option : OPTIONS) {
            String code = option.get("code");
            String name = option.get("name");
            if (code != null && trimmed.equalsIgnoreCase(code)) {
                return CountryResolver.normalize(code);
            }
            if (name != null && trimmed.equalsIgnoreCase(name)) {
                return CountryResolver.normalize(code);
            }
        }
        return null;
    }

    /**
     * 写入 task_records.country_key 的标准化值：优先目录代码，否则保留大写原文。
     */
    public static String normalizeCountryKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String resolved = resolveCountryCodeFromPays(raw);
        if (resolved != null && !"default".equalsIgnoreCase(resolved)) {
            return resolved;
        }
        return normalizeToken(raw);
    }

    /**
     * 权限与查询过滤：将目录国家代码展开为可命中历史数据的匹配 token 集合。
     */
    public static List<String> expandMatchTokens(Collection<String> catalogCodes) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (catalogCodes == null) {
            return new ArrayList<>();
        }
        for (String raw : catalogCodes) {
            if (raw == null || raw.trim().isEmpty() || "default".equalsIgnoreCase(raw.trim())) {
                continue;
            }
            String code = resolveCountryCodeFromPays(raw);
            if (code == null && isSupported(raw)) {
                code = CountryResolver.normalize(raw);
            }
            if (code == null) {
                tokens.add(normalizeToken(raw));
                continue;
            }
            addTokensForCode(tokens, code);
        }
        return new ArrayList<>(tokens);
    }

    private static void addTokensForCode(Set<String> tokens, String code) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        String upperCode = code.trim().toUpperCase(Locale.ROOT);
        tokens.add(upperCode);

        String paysLabel = PAYS_LABELS.get(upperCode);
        if (paysLabel != null) {
            tokens.add(paysLabel);
            tokens.add(normalizeToken(paysLabel));
        }

        for (Map<String, String> option : OPTIONS) {
            if (upperCode.equalsIgnoreCase(option.get("code"))) {
                String name = option.get("name");
                if (name != null && !name.trim().isEmpty()) {
                    tokens.add(name.trim());
                    tokens.add(normalizeToken(name));
                }
                break;
            }
        }

        for (Map.Entry<String, String> entry : LEGACY_ALIASES.entrySet()) {
            if (upperCode.equalsIgnoreCase(entry.getValue())) {
                tokens.add(entry.getKey());
            }
        }
    }

    private static String normalizeToken(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
