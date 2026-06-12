package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.response.ConfirmValidationIssueDTO;
import com.attendance.mapper.PluginConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.attendance.util.RecognizedFieldSanitizer;

@Service
public class ConfirmValidationService {

    static final String CONFIG_KEY = "confirm_validation_config";

    private static final List<String> NORMAL_MARK_TOKENS = Arrays.asList(
            "正常", "normal", "normale", "normaal");

    private static final Map<String, String[]> FIELD_ALIASES = new HashMap<>();

    static {
        FIELD_ALIASES.put("NOM_PRENOM", new String[]{"NOM_PRENOM", "Name"});
        FIELD_ALIASES.put("Date", new String[]{"Date", "WorkDate"});
        FIELD_ALIASES.put("DEPAR", new String[]{"DEPAR", "DEPART"});
    }

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    public ConfirmValidationConfigDTO getConfig() {
        String raw = pluginConfigMapper.selectValue(CONFIG_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultConfig();
        }
        try {
            ConfirmValidationConfigDTO parsed = JSON.parseObject(raw, ConfirmValidationConfigDTO.class);
            return sanitizeConfig(parsed);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    public void saveConfig(ConfirmValidationConfigDTO incoming) {
        ConfirmValidationConfigDTO sanitized = sanitizeConfig(incoming);
        pluginConfigMapper.upsertValue(
                CONFIG_KEY,
                JSON.toJSONString(sanitized),
                "json",
                "确认任务必填校验配置");
    }

    public void validateConfirmRecords(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        ConfirmValidationConfigDTO config = getConfig();
        List<ConfirmValidationIssueDTO> issues = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            List<String> missing = getMissingRequiredFieldKeys(record, config);
            if (!missing.isEmpty()) {
                ConfirmValidationIssueDTO issue = new ConfirmValidationIssueDTO();
                issue.setLine(i + 1);
                issue.setNo(firstNonBlank(record, "NO"));
                issue.setName(firstNonBlank(record, "NOM_PRENOM", "Name"));
                issue.setFields(missing);
                issues.add(issue);
            }
        }
        if (!issues.isEmpty()) {
            Map<String, Object> args = new HashMap<>();
            args.put("issueCount", issues.size());
            args.put("issues", issues);
            throw new BusinessException(400, ErrorKeys.CONFIRM_REQUIRED_FIELDS_MISSING, args);
        }
    }

    List<String> getMissingRequiredFieldKeys(Map<String, Object> record, ConfirmValidationConfigDTO config) {
        if (isValidationExempt(record, config)) {
            return new ArrayList<>();
        }
        List<String> missing = new ArrayList<>();
        for (String key : config.getRequiredFields()) {
            if (isFieldMissing(record, key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    boolean isValidationExempt(Map<String, Object> record, ConfirmValidationConfigDTO config) {
        if (record == null) {
            return true;
        }
        if (Boolean.TRUE.equals(record.get("isDeleted")) || Boolean.TRUE.equals(record.get("deleted"))) {
            return true;
        }
        String mark = getRecordMark(record);
        if (containsAnyToken(mark, "已删除", "deleted", "eliminado", "gelöscht", "verwijderd")) {
            return true;
        }
        if (!Boolean.TRUE.equals(record.get("_restored"))
                && containsAnyToken(mark, "未出勤", "absent", "ausente", "abwesend", "afwezig")) {
            return true;
        }
        return false;
    }

    private ConfirmValidationConfigDTO defaultConfig() {
        ConfirmValidationConfigDTO dto = new ConfirmValidationConfigDTO();
        dto.setScope(ConfirmValidationConfigDTO.SCOPE_EXCEPT_DELETED_ABSENT);
        dto.setRequiredFields(new ArrayList<>(ConfirmValidationConfigDTO.defaultRequiredFields()));
        return dto;
    }

    private ConfirmValidationConfigDTO sanitizeConfig(ConfirmValidationConfigDTO incoming) {
        ConfirmValidationConfigDTO dto = defaultConfig();
        if (incoming == null) {
            return dto;
        }
        if (incoming.getScope() != null && !incoming.getScope().trim().isEmpty()) {
            dto.setScope(incoming.getScope().trim());
        }
        if (incoming.getRequiredFields() != null) {
            Set<String> allowed = new LinkedHashSet<>(ConfirmValidationConfigDTO.ALL_FIELD_KEYS);
            List<String> next = new ArrayList<>();
            for (String field : incoming.getRequiredFields()) {
                if (field != null && allowed.contains(field) && !next.contains(field)) {
                    next.add(field);
                }
            }
            if (!next.isEmpty()) {
                dto.setRequiredFields(next);
            }
        }
        return dto;
    }

    private boolean isFieldMissing(Map<String, Object> record, String key) {
        if ("PAUSE".equals(key)) {
            return isPauseMissing(record.get("PAUSE"));
        }
        String[] aliases = FIELD_ALIASES.get(key);
        if (aliases != null) {
            return !hasFilledText(firstNonBlank(record, aliases));
        }
        return !hasFilledText(record.get(key));
    }

    static boolean isPauseMissing(Object value) {
        if (value instanceof Number) {
            return false;
        }
        String s = stringValue(value);
        if (RecognizedFieldSanitizer.isUnrecognized(s)) {
            return true;
        }
        if ("0".equals(s) || "00:00".equals(s) || "0:00".equals(s)) {
            return false;
        }
        return false;
    }

    private static boolean hasFilledText(String s) {
        return !RecognizedFieldSanitizer.isUnrecognized(s);
    }

    private static boolean hasFilledText(Object value) {
        return hasFilledText(stringValue(value));
    }

    private static String firstNonBlank(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            String v = stringValue(record.get(key));
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String getRecordMark(Map<String, Object> record) {
        String mark = stringValue(record.get("SmartMark"));
        if (mark.isEmpty()) {
            mark = stringValue(record.get("Mark"));
        }
        return mark;
    }

    static boolean markContainsNormal(String mark) {
        if (mark == null || mark.isEmpty()) {
            return false;
        }
        String[] parts = mark.split(";");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            for (String token : NORMAL_MARK_TOKENS) {
                if (trimmed.contains(token.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAnyToken(String text, String... tokens) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
