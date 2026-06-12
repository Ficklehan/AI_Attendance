package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.mapper.PluginConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NightShiftConfigService {

    static final String CONFIG_KEY = "night_shift_config";
    private static final Pattern CLOCK = Pattern.compile("^([0-1]?\\d|2[0-3]):([0-5]\\d)$");

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    public NightShiftConfigDTO getConfig() {
        String raw = pluginConfigMapper.selectValue(CONFIG_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return NightShiftConfigDTO.defaults();
        }
        try {
            return sanitize(JSON.parseObject(raw, NightShiftConfigDTO.class));
        } catch (Exception e) {
            return NightShiftConfigDTO.defaults();
        }
    }

    public void saveConfig(NightShiftConfigDTO incoming) {
        pluginConfigMapper.upsertValue(
                CONFIG_KEY,
                JSON.toJSONString(sanitize(incoming)),
                "json",
                "夜班判定规则");
    }

    NightShiftConfigDTO sanitize(NightShiftConfigDTO incoming) {
        NightShiftConfigDTO dto = NightShiftConfigDTO.defaults();
        if (incoming == null) {
            return dto;
        }
        String start = normalizeClock(incoming.getStartTime(), dto.getStartTime());
        String end = normalizeClock(incoming.getEndTime(), dto.getEndTime());
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setCrossMidnight(incoming.isCrossMidnight());
        dto.setUseScheduleColumn(incoming.isUseScheduleColumn());
        return dto;
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
