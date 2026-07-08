package com.attendance.service;

import com.attendance.config.CountryCatalog;
import com.attendance.config.RoleDataScopeDimensions;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.mapper.RoleDataScopeMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.security.DataScopeContext;
import com.attendance.security.SecurityUtils;
import com.attendance.util.CountryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataScopeService {

    private static final List<String> KNOWN_ROLES = Arrays.asList("admin", "user");
    private static final List<String> DIMENSIONS = RoleDataScopeDimensions.ALL;

    @Autowired
    private RoleDataScopeMapper roleDataScopeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TaskRecordMapper taskRecordMapper;

    @Autowired
    private UserRoleService userRoleService;

    public DataScopeContext resolveForCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
        return resolveForUserId(userId);
    }

    public DataScopeContext resolveForUserId(String userId) {
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_NOT_FOUND);
        }
        List<String> roles = userRoleService.getRoleKeysForUserId(userId);
        return resolveForRoles(roles, userId);
    }

    public DataScopeContext resolveForRoles(List<String> roles, String currentUserId) {
        if (roles == null || roles.isEmpty()) {
            return resolveForRole("user", currentUserId);
        }
        DataScopeContext merged = null;
        for (String role : roles) {
            DataScopeContext ctx = resolveForRole(role, currentUserId);
            if (ctx.isAllUsers()) {
                return DataScopeContext.allUsers();
            }
            if (merged == null) {
                merged = copyScope(ctx);
            } else {
                mergeScope(merged, ctx);
            }
        }
        if (merged == null) {
            return resolveForRole("user", currentUserId);
        }
        if (!merged.hasOwnerUserFilter() && !merged.isRecordDimensionFilter()
                && currentUserId != null && !currentUserId.isEmpty()) {
            merged.setOwnerUserIds(Collections.singletonList(currentUserId));
        }
        return merged;
    }

    public DataScopeContext resolveForRole(String role, String currentUserId) {
        String normalizedRole = normalizeRole(role);
        if ("admin".equals(normalizedRole)) {
            return DataScopeContext.allUsers();
        }

        String scopeType = roleDataScopeMapper.selectScopeType(normalizedRole);
        if (scopeType == null || "all".equalsIgnoreCase(scopeType)) {
            return DataScopeContext.allUsers();
        }

        DataScopeContext ctx = new DataScopeContext();
        ctx.setAllUsers(false);
        ctx.setViewerUserId(currentUserId);
        List<Map<String, String>> rules = roleDataScopeMapper.selectRulesByRole(normalizedRole);
        Set<String> owners = new LinkedHashSet<>();
        Set<String> countries = new LinkedHashSet<>();
        Set<String> warehouses = new LinkedHashSet<>();
        Set<String> agencies = new LinkedHashSet<>();
        Set<String> workRegions = new LinkedHashSet<>();

        for (Map<String, String> row : rules) {
            if (row == null) {
                continue;
            }
            String dimension = row.get("dimension");
            String value = row.get("value");
            if (dimension == null || value == null || value.trim().isEmpty()) {
                continue;
            }
            String trimmed = value.trim();
            switch (dimension) {
                case "owner_user":
                    if (DataScopeContext.SELF_TOKEN.equals(trimmed)) {
                        if (currentUserId != null && !currentUserId.isEmpty()) {
                            owners.add(currentUserId);
                        }
                    } else {
                        owners.add(trimmed);
                    }
                    break;
                case "country":
                    String countryCode = CountryCatalog.resolveCountryCodeFromPays(trimmed);
                    countries.add(countryCode != null ? countryCode : CountryResolver.normalize(trimmed));
                    break;
                case "warehouse":
                    warehouses.add(trimmed);
                    break;
                case "agency":
                    agencies.add(trimmed);
                    break;
                case "work_region":
                    String regionCode = CountryCatalog.resolveCountryCodeFromPays(trimmed);
                    workRegions.add(regionCode != null ? regionCode : CountryResolver.normalize(trimmed));
                    break;
                default:
                    break;
            }
        }

        ctx.setOwnerUserIds(new ArrayList<>(owners));
        Set<String> unifiedWorkCountries = new LinkedHashSet<>();
        for (String code : countries) {
            String normalized = CountryResolver.normalize(code);
            if (normalized != null && !normalized.isEmpty()) {
                unifiedWorkCountries.add(normalized);
            }
        }
        for (String code : workRegions) {
            String normalized = CountryResolver.normalize(code);
            if (normalized != null && !normalized.isEmpty()) {
                unifiedWorkCountries.add(normalized);
            }
        }
        List<String> unifiedList = new ArrayList<>(unifiedWorkCountries);
        ctx.setCountries(unifiedList);
        ctx.setCountryMatchTokens(CountryCatalog.expandMatchTokens(unifiedList));
        ctx.setWorkRegions(unifiedList);
        ctx.setWarehouses(new ArrayList<>(warehouses));
        ctx.setAgencies(new ArrayList<>(agencies));
        // restricted 但未配置任何规则时，默认仅能看本人数据（避免列表 SQL 退化为 AND 1=0）
        if (!ctx.hasOwnerUserFilter() && !ctx.isRecordDimensionFilter()
                && currentUserId != null && !currentUserId.isEmpty()) {
            ctx.setOwnerUserIds(Collections.singletonList(currentUserId));
        }
        return ctx;
    }

    public boolean canAccessTask(String viewerUserId, Task task) {
        if (task == null) {
            return false;
        }
        // 创建者始终可访问自己的任务（识别进行中尚无 task_records，不能按记录维度过滤）
        if (viewerUserId != null && viewerUserId.equals(task.getUserId())) {
            return true;
        }
        DataScopeContext scope = resolveForUserId(viewerUserId);
        if (scope.isAllUsers()) {
            return true;
        }
        if (!scope.hasOwnerUserFilter() && !scope.isRecordDimensionFilter()) {
            return false;
        }
        if (scope.hasOwnerUserFilter()) {
            String ownerId = task.getUserId();
            if (ownerId == null || !scope.getOwnerUserIds().contains(ownerId)) {
                return false;
            }
        }
        if (scope.isRecordDimensionFilter()) {
            if (scope.getCountries() != null && !scope.getCountries().isEmpty()) {
                String promptCountry = CountryCatalog.resolveCountryCodeFromPays(task.getPromptCountry());
                if (promptCountry == null) {
                    promptCountry = CountryResolver.normalize(task.getPromptCountry());
                }
                if (promptCountry != null && scope.getCountries().contains(promptCountry)) {
                    return true;
                }
            }
            long matched = taskRecordMapper.countRecordsMatchingScope(
                    task.getTaskId(), scope.getCountryMatchTokens(), scope.getWarehouses(), scope.getAgencies());
            return matched > 0;
        }
        return true;
    }

    public List<String> knownRoles() {
        return KNOWN_ROLES;
    }

    public List<String> knownDimensions() {
        return DIMENSIONS;
    }

    private static String normalizeRole(String role) {
        return SystemRoleService.normalizeRoleKey(role);
    }

    private static DataScopeContext copyScope(DataScopeContext source) {
        DataScopeContext copy = new DataScopeContext();
        copy.setAllUsers(source.isAllUsers());
        copy.setViewerUserId(source.getViewerUserId());
        copy.setOwnerUserIds(source.getOwnerUserIds());
        copy.setCountries(source.getCountries());
        copy.setCountryMatchTokens(source.getCountryMatchTokens());
        copy.setWarehouses(source.getWarehouses());
        copy.setAgencies(source.getAgencies());
        copy.setWorkRegions(source.getWorkRegions());
        return copy;
    }

    private static void mergeScope(DataScopeContext target, DataScopeContext source) {
        Set<String> owners = new LinkedHashSet<>(target.getOwnerUserIds());
        owners.addAll(source.getOwnerUserIds());
        target.setOwnerUserIds(new ArrayList<>(owners));

        Set<String> unifiedWorkCountries = new LinkedHashSet<>();
        for (String code : target.getCountries()) {
            String normalized = CountryResolver.normalize(code);
            if (normalized != null && !normalized.isEmpty()) {
                unifiedWorkCountries.add(normalized);
            }
        }
        for (String code : target.getWorkRegions()) {
            String normalized = CountryResolver.normalize(code);
            if (normalized != null && !normalized.isEmpty()) {
                unifiedWorkCountries.add(normalized);
            }
        }
        for (String code : source.getCountries()) {
            String normalized = CountryResolver.normalize(code);
            if (normalized != null && !normalized.isEmpty()) {
                unifiedWorkCountries.add(normalized);
            }
        }
        for (String code : source.getWorkRegions()) {
            String normalized = CountryResolver.normalize(code);
            if (normalized != null && !normalized.isEmpty()) {
                unifiedWorkCountries.add(normalized);
            }
        }
        List<String> unifiedList = new ArrayList<>(unifiedWorkCountries);
        target.setCountries(unifiedList);
        target.setCountryMatchTokens(CountryCatalog.expandMatchTokens(unifiedList));
        target.setWorkRegions(unifiedList);

        Set<String> warehouses = new LinkedHashSet<>(target.getWarehouses());
        warehouses.addAll(source.getWarehouses());
        target.setWarehouses(new ArrayList<>(warehouses));

        Set<String> agencies = new LinkedHashSet<>(target.getAgencies());
        agencies.addAll(source.getAgencies());
        target.setAgencies(new ArrayList<>(agencies));
    }
}
