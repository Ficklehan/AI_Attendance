package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.CountryCatalog;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.mapper.PluginConfigMapper;
import com.attendance.util.CountryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NightShiftConfigService {

    static final String CONFIG_KEY = "night_shift_config";
    private static final Pattern CLOCK = Pattern.compile("^([0-1]?\\d|2[0-3]):([0-5]\\d)$");

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    /** 全局默认规则（不含各国覆盖，供管理端展示默认段）。 */
    public NightShiftConfigDTO getConfig() {
        return sanitizeGlobal(loadStored());
    }

    /** 管理端：全局默认 + 各国覆盖表。 */
    public NightShiftConfigDTO getAdminConfig() {
        NightShiftConfigDTO stored = loadStored();
        NightShiftConfigDTO admin = sanitizeGlobal(stored);
        admin.setByCountry(sanitizeByCountry(stored.getByCountry()));
        return admin;
    }

    /** 运行时：按国家取生效规则（有该国覆盖则用覆盖，否则全局默认）。 */
    public NightShiftConfigDTO getConfigForCountry(String country) {
        NightShiftConfigDTO stored = loadStored();
        String code = normalizeCountryKey(country);
        if (!"default".equals(code)) {
            Map<String, NightShiftConfigDTO> overrides = stored.getByCountry();
            if (overrides != null) {
                NightShiftConfigDTO override = overrides.get(code);
                if (override != null) {
                    return sanitize(override);
                }
            }
        }
        return sanitizeGlobal(stored);
    }

    public void saveConfig(NightShiftConfigDTO incoming) {
        if (incoming == null) {
            return;
        }
        NightShiftConfigDTO stored = loadStored();
        NightShiftConfigDTO global = sanitize(incoming);
        global.setByCountry(null);
        stored.setStartTime(global.getStartTime());
        stored.setEndTime(global.getEndTime());
        stored.setCrossMidnight(global.isCrossMidnight());
        stored.setUseScheduleColumn(global.isUseScheduleColumn());
        if (incoming.getByCountry() != null) {
            stored.setByCountry(sanitizeByCountry(incoming.getByCountry()));
        }
        persist(stored);
    }

    NightShiftConfigDTO sanitize(NightShiftConfigDTO incoming) {
        NightShiftConfigDTO dto = NightShiftConfigDTO.defaults();
        if (incoming == null) {
            return dto;
        }
        dto.setStartTime(normalizeClock(incoming.getStartTime(), dto.getStartTime()));
        dto.setEndTime(normalizeClock(incoming.getEndTime(), dto.getEndTime()));
        dto.setCrossMidnight(incoming.isCrossMidnight());
        dto.setUseScheduleColumn(incoming.isUseScheduleColumn());
        return dto;
    }

    private NightShiftConfigDTO sanitizeGlobal(NightShiftConfigDTO incoming) {
        NightShiftConfigDTO dto = sanitize(incoming);
        dto.setByCountry(null);
        return dto;
    }

    private Map<String, NightShiftConfigDTO> sanitizeByCountry(Map<String, NightShiftConfigDTO> raw) {
        Map<String, NightShiftConfigDTO> sanitized = new LinkedHashMap<>();
        if (raw == null) {
            return sanitized;
        }
        for (Map.Entry<String, NightShiftConfigDTO> entry : raw.entrySet()) {
            String code = normalizeCountryKey(entry.getKey());
            if ("default".equals(code) || !CountryCatalog.isSupported(code)) {
                continue;
            }
            NightShiftConfigDTO rule = coerceRule(entry.getValue());
            if (rule != null) {
                sanitized.put(code, sanitize(rule));
            }
        }
        return sanitized;
    }

    /** FastJSON / Jackson 反序列化 Map 值时可能是 JSONObject 或 LinkedHashMap。 */
    private NightShiftConfigDTO coerceRule(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof NightShiftConfigDTO) {
            return (NightShiftConfigDTO) raw;
        }
        if (raw instanceof JSONObject) {
            return ((JSONObject) raw).toJavaObject(NightShiftConfigDTO.class);
        }
        return JSON.parseObject(JSON.toJSONString(raw), NightShiftConfigDTO.class);
    }

    private Map<String, NightShiftConfigDTO> parseByCountry(JSONObject json) {
        Map<String, NightShiftConfigDTO> byCountry = new LinkedHashMap<>();
        if (json == null) {
            return byCountry;
        }
        JSONObject raw = json.getJSONObject("byCountry");
        if (raw == null || raw.isEmpty()) {
            return byCountry;
        }
        for (String key : raw.keySet()) {
            String code = normalizeCountryKey(key);
            if ("default".equals(code) || !CountryCatalog.isSupported(code)) {
                continue;
            }
            JSONObject ruleJson = raw.getJSONObject(key);
            if (ruleJson == null) {
                continue;
            }
            NightShiftConfigDTO rule = ruleJson.toJavaObject(NightShiftConfigDTO.class);
            if (rule != null) {
                byCountry.put(code, rule);
            }
        }
        return byCountry;
    }

    private NightShiftConfigDTO loadStored() {
        String raw = pluginConfigMapper.selectValue(CONFIG_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return NightShiftConfigDTO.defaults();
        }
        try {
            JSONObject json = JSON.parseObject(raw);
            NightShiftConfigDTO dto = json.toJavaObject(NightShiftConfigDTO.class);
            if (dto == null) {
                return NightShiftConfigDTO.defaults();
            }
            dto.setByCountry(parseByCountry(json));
            return dto;
        } catch (Exception e) {
            return NightShiftConfigDTO.defaults();
        }
    }

    private void persist(NightShiftConfigDTO stored) {
        NightShiftConfigDTO toSave = new NightShiftConfigDTO();
        NightShiftConfigDTO global = sanitizeGlobal(stored);
        toSave.setStartTime(global.getStartTime());
        toSave.setEndTime(global.getEndTime());
        toSave.setCrossMidnight(global.isCrossMidnight());
        toSave.setUseScheduleColumn(global.isUseScheduleColumn());
        toSave.setByCountry(sanitizeByCountry(stored.getByCountry()));
        pluginConfigMapper.upsertValue(
                CONFIG_KEY,
                JSON.toJSONString(toSave),
                "json",
                "夜班判定规则");
    }

    private static String normalizeCountryKey(String country) {
        if (country == null || country.trim().isEmpty()) {
            return "default";
        }
        return CountryResolver.normalize(country);
    }

    private static String normalizeClock(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String trimmed = value.trim();
        Matcher matcher = CLOCK.matcher(trimmed);
        if (!matcher.matches()) {
            return fallback;
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        return String.format("%02d:%02d", hour, minute);
    }
}
