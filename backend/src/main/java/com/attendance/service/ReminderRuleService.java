package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.request.ReminderRuleRequest;
import com.attendance.dto.response.ReminderRuleDTO;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.User;
import com.attendance.config.CountryCatalog;
import com.attendance.entity.SystemRole;
import com.attendance.mapper.ReminderRuleMapper;
import com.attendance.mapper.SystemRoleMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.security.SecurityUtils;
import com.attendance.util.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ReminderRuleService {

    private static final Pattern INTERVAL_UNIT_PATTERN = Pattern.compile("(?i)minute|hour|day|week");

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    private ReminderRuleMapper reminderRuleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ReminderScheduleService reminderScheduleService;

    public List<ReminderRuleDTO> listRules() {
        requireReminderConfig();
        return reminderRuleMapper.selectAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ReminderRuleDTO getRule(String id) {
        requireReminderConfig();
        ReminderRule rule = reminderRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, ErrorKeys.VALIDATION_FAILED);
        }
        return toDto(rule);
    }

    @Transactional
    public ReminderRuleDTO createRule(ReminderRuleRequest request) {
        requireReminderConfig();
        validateRequest(request);
        validateScope(request);
        ReminderRule rule = fromRequest(request);
        rule.setId(IdGenerator.generateId());
        rule.setCreatedBy(SecurityUtils.getCurrentUserId());
        reminderRuleMapper.insertRule(rule);
        saveRecipients(rule.getId(), request.getRecipientUserIds());
        reminderScheduleService.reconcileRule(rule.getId());
        return toDto(reminderRuleMapper.selectById(rule.getId()));
    }

    @Transactional
    public ReminderRuleDTO updateRule(String id, ReminderRuleRequest request) {
        requireReminderConfig();
        ReminderRule existing = reminderRuleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, ErrorKeys.VALIDATION_FAILED);
        }
        validateRequest(request);
        validateScope(request);
        ReminderRule rule = fromRequest(request);
        rule.setId(id);
        reminderRuleMapper.updateRule(rule);
        reminderRuleMapper.deleteRecipients(id);
        saveRecipients(id, request.getRecipientUserIds());
        reminderScheduleService.reconcileRule(id);
        return toDto(reminderRuleMapper.selectById(id));
    }

    public void setEnabled(String id, boolean enabled) {
        requireReminderConfig();
        if (reminderRuleMapper.selectById(id) == null) {
            throw new BusinessException(404, ErrorKeys.VALIDATION_FAILED);
        }
        reminderRuleMapper.updateEnabled(id, enabled);
        if (enabled) {
            reminderScheduleService.reconcileRule(id);
        } else {
            reminderScheduleService.onRuleDisabledOrDeleted(id);
        }
    }

    @Transactional
    public void deleteRule(String id) {
        requireReminderConfig();
        if (reminderRuleMapper.selectById(id) == null) {
            throw new BusinessException(404, ErrorKeys.VALIDATION_FAILED);
        }
        reminderRuleMapper.deleteRecipients(id);
        reminderScheduleService.onRuleDisabledOrDeleted(id);
        reminderRuleMapper.deleteById(id);
    }

    private void requireReminderConfig() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userMapper.selectUserById(userId);
        permissionService.requirePermission(user, ReminderSupport.PERMISSION_KEY);
    }

    private void validateRequest(ReminderRuleRequest request) {
        if (!ReminderSupport.isValidIntervalValue(request.getIntervalValue())) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
        String unit = request.getIntervalUnit();
        if (unit == null || !INTERVAL_UNIT_PATTERN.matcher(unit).matches()) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
        if (request.getRecipientUserIds() == null || request.getRecipientUserIds().isEmpty()) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
        validateScheduleHour(request);
        if (!hasAnyOperatorTemplate(request)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
        Set<String> activeIds = userMapper.selectActiveUsers().stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        for (String uid : request.getRecipientUserIds()) {
            if (!activeIds.contains(uid)) {
                throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
            }
        }
    }

    private void validateScheduleHour(ReminderRuleRequest request) {
        String unit = ReminderSupport.normalizeIntervalUnit(request.getIntervalUnit());
        Integer hour = request.getScheduleHourOfDay();
        if (ReminderSupport.supportsScheduleHour(unit)) {
            if (hour == null) {
                throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
            }
            if (hour < 0 || hour > 23) {
                throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
            }
            return;
        }
        if (hour != null) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
    }

    private void validateScope(ReminderRuleRequest request) {
        Set<String> validRoles = new HashSet<>();
        validRoles.add("admin");
        validRoles.add("user");
        for (SystemRole role : systemRoleMapper.selectAll()) {
            if (role.getRoleKey() != null) {
                validRoles.add(role.getRoleKey().trim());
            }
        }
        if (request.getScopeCountries() != null) {
            for (String code : request.getScopeCountries()) {
                if (code == null || code.trim().isEmpty()) {
                    continue;
                }
                if (!CountryCatalog.isSupported(code.trim())) {
                    throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
                }
            }
        }
        if (request.getScopeRoles() != null) {
            for (String roleKey : request.getScopeRoles()) {
                if (roleKey == null || roleKey.trim().isEmpty()) {
                    continue;
                }
                if (!validRoles.contains(roleKey.trim())) {
                    throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
                }
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toScopeJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        return cleaned.isEmpty() ? null : JSON.toJSONString(cleaned);
    }

    private void saveRecipients(String ruleId, List<String> userIds) {
        Set<String> seen = new HashSet<>();
        for (String uid : userIds) {
            if (uid != null && seen.add(uid)) {
                reminderRuleMapper.insertRecipient(ruleId, uid);
            }
        }
    }

    private boolean hasAnyOperatorTemplate(ReminderRuleRequest request) {
        Map<String, String> locales = normalizeTemplateLocales(request);
        if (!locales.isEmpty()) {
            return true;
        }
        return request.getMessageTemplate() != null && !request.getMessageTemplate().trim().isEmpty();
    }

    private Map<String, String> normalizeTemplateLocales(ReminderRuleRequest request) {
        Map<String, String> operatorLocales = new LinkedHashMap<>();
        if (request.getMessageTemplateLocales() != null) {
            for (Map.Entry<String, String> entry : request.getMessageTemplateLocales().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String value = entry.getValue().trim();
                if (!value.isEmpty()) {
                    operatorLocales.put(entry.getKey().trim(), value);
                }
            }
        }
        if (operatorLocales.isEmpty()
                && request.getMessageTemplate() != null
                && !request.getMessageTemplate().trim().isEmpty()) {
            operatorLocales.put(ReminderLocaleSupport.DEFAULT_LOCALE, request.getMessageTemplate().trim());
        }
        return operatorLocales;
    }

    private Map<String, String> normalizeSupervisorLocales(ReminderRuleRequest request) {
        Map<String, String> supervisorLocales = new LinkedHashMap<>();
        if (request.getMessageTemplateSupervisorLocales() != null) {
            for (Map.Entry<String, String> entry : request.getMessageTemplateSupervisorLocales().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String value = entry.getValue().trim();
                if (!value.isEmpty()) {
                    supervisorLocales.put(entry.getKey().trim(), value);
                }
            }
        }
        if (supervisorLocales.isEmpty()) {
            String legacy = trimToNull(request.getMessageTemplateSupervisor());
            if (legacy != null) {
                supervisorLocales.put(ReminderLocaleSupport.DEFAULT_LOCALE, legacy);
            }
        }
        return supervisorLocales;
    }

    private ReminderRule fromRequest(ReminderRuleRequest request) {
        Map<String, String> operatorLocales = normalizeTemplateLocales(request);
        Map<String, String> supervisorLocales = normalizeSupervisorLocales(request);
        ReminderRule rule = new ReminderRule();
        rule.setName(request.getName().trim());
        rule.setDescription(request.getDescription());
        rule.setTaskStatusesJson(JSON.toJSONString(request.getTaskStatuses()));
        rule.setScopeCountriesJson(toScopeJson(request.getScopeCountries()));
        rule.setScopeRolesJson(toScopeJson(request.getScopeRoles()));
        rule.setIntervalValue(ReminderSupport.normalizeIntervalValue(request.getIntervalValue()));
        rule.setIntervalUnit(ReminderSupport.normalizeIntervalUnit(request.getIntervalUnit()));
        rule.setScheduleHourOfDay(ReminderSupport.normalizeScheduleHour(
                request.getScheduleHourOfDay(), request.getIntervalUnit()));
        rule.setMessageTemplateLocalesJson(ReminderLocaleSupport.toTemplateJson(operatorLocales));
        rule.setMessageTemplateSupervisorLocalesJson(ReminderLocaleSupport.toTemplateJson(supervisorLocales));
        rule.setMessageTemplate(ReminderLocaleSupport.primaryTemplateForStorage(operatorLocales));
        rule.setMessageTemplateSupervisor(trimToNull(
                ReminderLocaleSupport.primarySupervisorTemplateForStorage(supervisorLocales)));
        rule.setIncludeTaskCreator(Boolean.TRUE.equals(request.getIncludeTaskCreator()));
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        return rule;
    }

    private ReminderRuleDTO toDto(ReminderRule rule) {
        ReminderRuleDTO dto = new ReminderRuleDTO();
        dto.setId(rule.getId());
        dto.setName(rule.getName());
        dto.setDescription(rule.getDescription());
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        dto.setTaskStatuses(statuses != null ? statuses : new ArrayList<>());
        dto.setScopeCountries(ReminderSupport.parseScopeList(rule.getScopeCountriesJson()));
        dto.setScopeRoles(ReminderSupport.parseScopeList(rule.getScopeRolesJson()));
        dto.setIntervalValue(rule.getIntervalValue());
        dto.setIntervalUnit(rule.getIntervalUnit());
        dto.setScheduleHourOfDay(rule.getScheduleHourOfDay());
        dto.setMessageTemplate(rule.getMessageTemplate());
        dto.setMessageTemplateSupervisor(rule.getMessageTemplateSupervisor());
        dto.setMessageTemplateLocales(ReminderLocaleSupport.parseTemplateMap(rule.getMessageTemplateLocalesJson()));
        dto.setMessageTemplateSupervisorLocales(
                ReminderLocaleSupport.parseTemplateMap(rule.getMessageTemplateSupervisorLocalesJson()));
        if (dto.getMessageTemplateLocales().isEmpty() && rule.getMessageTemplate() != null) {
            dto.getMessageTemplateLocales().put(ReminderLocaleSupport.DEFAULT_LOCALE, rule.getMessageTemplate());
        }
        if (dto.getMessageTemplateSupervisorLocales().isEmpty() && rule.getMessageTemplateSupervisor() != null) {
            dto.getMessageTemplateSupervisorLocales().put(
                    ReminderLocaleSupport.DEFAULT_LOCALE, rule.getMessageTemplateSupervisor());
        }
        dto.setIncludeTaskCreator(rule.isIncludeTaskCreator());
        dto.setEnabled(rule.isEnabled());
        List<String> recipients = reminderRuleMapper.selectRecipientUserIds(rule.getId());
        dto.setRecipientUserIds(recipients);
        dto.setRecipientCount(recipients != null ? recipients.size() : 0);
        dto.setLastRunAt(rule.getLastRunAt());
        dto.setLastHitCount(rule.getLastHitCount());
        dto.setLastSentCount(rule.getLastSentCount());
        dto.setCreatedAt(rule.getCreatedAt());
        dto.setUpdatedAt(rule.getUpdatedAt());
        return dto;
    }
}
