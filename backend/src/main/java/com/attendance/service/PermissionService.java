package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.CountryCatalog;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.config.ConfigPathResolver;
import com.attendance.dto.request.RolePermissionsUpdateRequest;
import com.attendance.dto.response.RolePermissionsBundleDTO;
import com.attendance.entity.SystemRole;
import com.attendance.entity.User;
import com.attendance.mapper.SystemRoleMapper;
import com.attendance.security.AdminAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final String PERMISSIONS_FILE = "permissions.json";
    private static final String PERMISSIONS_BY_COUNTRY_FILE = "permissions-by-country.json";
    public static final String RECORD_CALIBRATE = "recordCalibrate";
    public static final String TASK_DELETE_CONFIRMED = "taskDeleteConfirmed";
    public static final String REMINDER_CONFIG = "reminderConfig";
    public static final String EMPLOYEES = "employees";

    @Autowired
    private ConfigPathResolver configPathResolver;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    @Lazy
    private UserRoleService userRoleService;

    @Autowired
    private MarkdownConfigService markdownConfigService;

    public RolePermissionsBundleDTO getRolePermissionsBundle() {
        RolePermissionsBundleDTO bundle = new RolePermissionsBundleDTO();
        bundle.setRoles(getRolePermissions());
        bundle.setByCountry(loadByCountryAll());
        return bundle;
    }

    public Map<String, Map<String, Boolean>> getRolePermissions() {
        return loadAll();
    }

    public void updateRolePermissionsBundle(RolePermissionsUpdateRequest request) {
        adminAuthService.requireAdmin();
        if (request == null) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            updateRolePermissions(request.getRoles());
        }
        if (request.getByCountry() != null) {
            updateCountryRolePermissions(request.getByCountry());
        }
    }

    public void updateRolePermissions(Map<String, Map<String, Boolean>> body) {
        adminAuthService.requireAdmin();
        if (body == null || body.isEmpty()) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }
        Map<String, Map<String, Boolean>> merged = loadAll();
        for (Map.Entry<String, Map<String, Boolean>> entry : body.entrySet()) {
            String role = normalizeRole(entry.getKey());
            if ("admin".equals(role)) {
                continue;
            }
            if (systemRoleMapper.selectByKey(role) == null) {
                throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                        Collections.singletonMap("detail", "unknown role: " + role));
            }
            Map<String, Boolean> defaults = defaultRole(role);
            Map<String, Boolean> incoming = entry.getValue() != null ? entry.getValue() : Collections.emptyMap();
            Map<String, Boolean> next = new LinkedHashMap<>(defaults);
            for (Map.Entry<String, Boolean> perm : incoming.entrySet()) {
                if (perm.getKey() != null && perm.getValue() != null) {
                    next.put(perm.getKey(), perm.getValue());
                }
            }
            merged.put(role, next);
        }
        try {
            saveAll(merged);
        } catch (IOException e) {
            log.error("保存 permissions.json 失败", e);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.SYSTEM_ERROR);
        }
    }

    public void updateCountryRolePermissions(Map<String, Map<String, Map<String, Boolean>>> body) {
        adminAuthService.requireAdmin();
        Map<String, Map<String, Map<String, Boolean>>> merged = loadByCountryAll();
        if (body != null) {
            for (Map.Entry<String, Map<String, Map<String, Boolean>>> roleEntry : body.entrySet()) {
                String role = normalizeRole(roleEntry.getKey());
                if ("admin".equals(role)) {
                    continue;
                }
                if (systemRoleMapper.selectByKey(role) == null) {
                    throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                            Collections.singletonMap("detail", "unknown role: " + role));
                }
                Map<String, Map<String, Boolean>> incomingCountries = roleEntry.getValue();
                if (incomingCountries == null) {
                    merged.remove(role);
                    continue;
                }
                Map<String, Map<String, Boolean>> roleCountries = merged.computeIfAbsent(role, k -> new LinkedHashMap<>());
                for (Map.Entry<String, Map<String, Boolean>> countryEntry : incomingCountries.entrySet()) {
                    String country = normalizeCountryCode(countryEntry.getKey());
                    if (country == null) {
                        continue;
                    }
                    Map<String, Boolean> incoming = countryEntry.getValue();
                    if (incoming == null || incoming.isEmpty()) {
                        roleCountries.remove(country);
                        continue;
                    }
                    Map<String, Boolean> defaults = defaultRole(role);
                    Map<String, Boolean> next = new LinkedHashMap<>(defaults);
                    for (Map.Entry<String, Boolean> perm : incoming.entrySet()) {
                        if (perm.getKey() != null && perm.getValue() != null) {
                            next.put(perm.getKey(), perm.getValue());
                        }
                    }
                    roleCountries.put(country, next);
                }
                if (roleCountries.isEmpty()) {
                    merged.remove(role);
                }
            }
        }
        try {
            saveByCountryAll(merged);
        } catch (IOException e) {
            log.error("保存 permissions-by-country.json 失败", e);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.SYSTEM_ERROR);
        }
    }

    public Map<String, Boolean> effectivePermissions(User user) {
        return effectivePermissions(user, resolveWorkingCountryCode(user));
    }

    public Map<String, Boolean> effectivePermissions(User user, String workingCountryCode) {
        if (user == null) {
            return Collections.emptyMap();
        }
        List<String> roles = userRoleService.getRoleKeysForUserId(user.getId());
        if (roles.contains("admin")) {
            Map<String, Boolean> adminPerms = new LinkedHashMap<>(defaultRole("admin"));
            adminPerms.put(RECORD_CALIBRATE, true);
            adminPerms.put(TASK_DELETE_CONFIRMED, true);
            adminPerms.put(REMINDER_CONFIG, true);
            adminPerms.put("aiConfig", true);
            adminPerms.put("feishuConfig", true);
            adminPerms.put("users", true);
            adminPerms.put("audit", true);
            adminPerms.put(EMPLOYEES, true);
            return adminPerms;
        }
        Map<String, Map<String, Boolean>> all = loadAll();
        Map<String, Boolean> effective = new LinkedHashMap<>();
        for (String role : roles) {
            Map<String, Boolean> rolePerms = all.getOrDefault(normalizeRole(role), defaultRole(role));
            for (Map.Entry<String, Boolean> entry : rolePerms.entrySet()) {
                if (entry.getKey() != null && Boolean.TRUE.equals(entry.getValue())) {
                    effective.put(entry.getKey(), true);
                }
            }
        }
        for (String role : roles) {
            Map<String, Boolean> defaults = defaultRole(role);
            for (String key : defaults.keySet()) {
                effective.putIfAbsent(key, false);
            }
        }
        return effective;
    }

    public boolean hasPermission(User user, String permissionKey) {
        if (user == null || permissionKey == null) {
            return false;
        }
        if (userRoleService.userHasRole(user.getId(), "admin")) {
            if (RECORD_CALIBRATE.equals(permissionKey)
                    || TASK_DELETE_CONFIRMED.equals(permissionKey)
                    || REMINDER_CONFIG.equals(permissionKey)) {
                return true;
            }
        }
        Map<String, Boolean> perms = effectivePermissions(user);
        return Boolean.TRUE.equals(perms.get(permissionKey));
    }

    public void requirePermission(User user, String permissionKey) {
        if (!hasPermission(user, permissionKey)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
        }
    }

    public void ensureRolePermissions(String roleKey) {
        if (roleKey == null || roleKey.trim().isEmpty()) {
            return;
        }
        String role = normalizeRole(roleKey);
        Map<String, Map<String, Boolean>> all = loadAll();
        if (all.containsKey(role)) {
            return;
        }
        all.put(role, defaultRole(role));
        try {
            saveAll(all);
        } catch (IOException e) {
            log.error("初始化角色功能权限失败 role={}", role, e);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.SYSTEM_ERROR);
        }
    }

    public void removeRolePermissions(String roleKey) {
        if (roleKey == null || roleKey.trim().isEmpty()) {
            return;
        }
        String role = normalizeRole(roleKey);
        Map<String, Map<String, Boolean>> all = loadAll();
        if (!all.containsKey(role)) {
            return;
        }
        all.remove(role);
        try {
            saveAll(all);
        } catch (IOException e) {
            log.error("删除角色功能权限失败 role={}", role, e);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.SYSTEM_ERROR);
        }
    }

    private void applyCountryOverrides(Map<String, Boolean> effective, String role, String workingCountryCode,
                                       Map<String, Map<String, Map<String, Boolean>>> byCountry) {
        String country = normalizeCountryCode(workingCountryCode);
        if (country == null || effective == null || byCountry == null) {
            return;
        }
        Map<String, Map<String, Boolean>> roleCountries = byCountry.get(role);
        if (roleCountries == null) {
            return;
        }
        Map<String, Boolean> countryPerms = roleCountries.get(country);
        if (countryPerms == null || countryPerms.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Boolean> entry : countryPerms.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                effective.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private String resolveWorkingCountryCode(User user) {
        if (user != null && user.getWorkingCountry() != null) {
            String fromUser = normalizeCountryCode(user.getWorkingCountry());
            if (fromUser != null) {
                return fromUser;
            }
        }
        if (markdownConfigService != null) {
            return normalizeCountryCode(
                    CountryCatalog.resolveGlobalDefaultCountry(markdownConfigService.getCurrentCountry()));
        }
        return null;
    }

    private static String normalizeCountryCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "default".equalsIgnoreCase(trimmed)) {
            return null;
        }
        String resolved = CountryCatalog.resolveCountryCodeFromPays(trimmed);
        if (resolved != null && !"default".equalsIgnoreCase(resolved)) {
            return resolved;
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        return CountryCatalog.isSupported(upper) ? upper : null;
    }

    private Map<String, Map<String, Map<String, Boolean>>> loadByCountryAll() {
        Path file = configPathResolver.resolveFile(PERMISSIONS_BY_COUNTRY_FILE);
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            if (root == null || root.isEmpty()) {
                return new LinkedHashMap<>();
            }
            Map<String, Map<String, Map<String, Boolean>>> result = new LinkedHashMap<>();
            for (String roleKey : root.keySet()) {
                JSONObject roleObj = root.getJSONObject(roleKey);
                if (roleObj == null || roleObj.isEmpty()) {
                    continue;
                }
                Map<String, Map<String, Boolean>> countries = new LinkedHashMap<>();
                for (String countryKey : roleObj.keySet()) {
                    String country = normalizeCountryCode(countryKey);
                    if (country == null) {
                        continue;
                    }
                    JSONObject permObj = roleObj.getJSONObject(countryKey);
                    if (permObj == null) {
                        continue;
                    }
                    Map<String, Boolean> perms = new LinkedHashMap<>();
                    for (String permKey : permObj.keySet()) {
                        perms.put(permKey, permObj.getBooleanValue(permKey));
                    }
                    countries.put(country, perms);
                }
                if (!countries.isEmpty()) {
                    result.put(normalizeRole(roleKey), countries);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("读取 permissions-by-country.json 失败", e);
            return new LinkedHashMap<>();
        }
    }

    private void saveByCountryAll(Map<String, Map<String, Map<String, Boolean>>> data) throws IOException {
        Path file = configPathResolver.resolveFile(PERMISSIONS_BY_COUNTRY_FILE);
        Files.createDirectories(file.getParent());
        String json = JSON.toJSONString(data != null ? data : Collections.emptyMap(), true);
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        log.info("已保存 role permissions by country: {}", file);
    }

    private Map<String, Map<String, Boolean>> loadAll() {
        Path file = configPathResolver.resolveFile(PERMISSIONS_FILE);
        if (!Files.isRegularFile(file)) {
            Map<String, Map<String, Boolean>> defaults = defaultAll();
            try {
                saveAll(defaults);
            } catch (IOException e) {
                log.warn("无法写入默认 permissions.json: {}", e.getMessage());
            }
            return defaults;
        }
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            Map<String, Map<String, Boolean>> result = new LinkedHashMap<>();
            for (SystemRole systemRole : systemRoleMapper.selectAll()) {
                String role = systemRole.getRoleKey();
                JSONObject roleObj = root.getJSONObject(role);
                Map<String, Boolean> map = new LinkedHashMap<>(defaultRole(role));
                if (roleObj != null) {
                    for (String key : roleObj.keySet()) {
                        map.put(key, roleObj.getBooleanValue(key));
                    }
                }
                if ("admin".equals(role)) {
                    map.put(RECORD_CALIBRATE, true);
                    map.put(TASK_DELETE_CONFIRMED, true);
                    map.put("aiConfig", true);
                    map.put("feishuConfig", true);
                    map.put("users", true);
                    map.put("audit", true);
                    map.put(REMINDER_CONFIG, true);
                    map.put(EMPLOYEES, true);
                }
                result.put(role, map);
            }
            return result;
        } catch (Exception e) {
            log.error("读取 permissions.json 失败", e);
            return defaultAll();
        }
    }

    private void saveAll(Map<String, Map<String, Boolean>> data) throws IOException {
        Path file = configPathResolver.resolveFile(PERMISSIONS_FILE);
        Files.createDirectories(file.getParent());
        String json = JSON.toJSONString(data, true);
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        log.info("已保存 role permissions: {}", file);
    }

    private Map<String, Map<String, Boolean>> defaultAll() {
        Map<String, Map<String, Boolean>> all = new LinkedHashMap<>();
        for (SystemRole systemRole : systemRoleMapper.selectAll()) {
            all.put(systemRole.getRoleKey(), defaultRole(systemRole.getRoleKey()));
        }
        if (all.isEmpty()) {
            all.put("admin", defaultRole("admin"));
            all.put("user", defaultRole("user"));
        }
        return all;
    }

    private Map<String, Boolean> defaultRole(String role) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        if ("admin".equals(role)) {
            m.put("tasks", true);
            m.put("country", true);
            m.put("aiConfig", true);
            m.put("feishuConfig", true);
            m.put("users", true);
            m.put("audit", true);
            m.put(RECORD_CALIBRATE, true);
            m.put(TASK_DELETE_CONFIRMED, true);
            m.put(REMINDER_CONFIG, true);
            m.put(EMPLOYEES, true);
        } else {
            m.put("tasks", true);
            m.put("country", true);
            m.put("aiConfig", false);
            m.put("feishuConfig", false);
            m.put("users", false);
            m.put("audit", false);
            m.put(RECORD_CALIBRATE, false);
            m.put(TASK_DELETE_CONFIRMED, false);
            m.put(REMINDER_CONFIG, false);
            m.put(EMPLOYEES, true);
        }
        return m;
    }

    private static String normalizeRole(String role) {
        return SystemRoleService.normalizeRoleKey(role);
    }
}
