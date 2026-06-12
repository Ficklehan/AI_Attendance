package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.config.ConfigPathResolver;
import com.attendance.entity.SystemRole;
import com.attendance.entity.User;
import com.attendance.mapper.SystemRoleMapper;
import com.attendance.security.AdminAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final String PERMISSIONS_FILE = "permissions.json";
    public static final String RECORD_CALIBRATE = "recordCalibrate";
    public static final String REMINDER_CONFIG = "reminderConfig";

    @Autowired
    private ConfigPathResolver configPathResolver;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    public Map<String, Map<String, Boolean>> getRolePermissions() {
        return loadAll();
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

    public Map<String, Boolean> effectivePermissions(User user) {
        if (user == null || user.getRole() == null) {
            return Collections.emptyMap();
        }
        String role = normalizeRole(user.getRole());
        Map<String, Map<String, Boolean>> all = loadAll();
        Map<String, Boolean> rolePerms = all.getOrDefault(role, defaultRole(role));
        return new LinkedHashMap<>(rolePerms);
    }

    public boolean hasPermission(User user, String permissionKey) {
        if (user == null || permissionKey == null) {
            return false;
        }
        if (RECORD_CALIBRATE.equals(permissionKey) && "admin".equals(normalizeRole(user.getRole()))) {
            return true;
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
                    map.put("aiConfig", true);
                    map.put("feishuConfig", true);
                    map.put("users", true);
                    map.put("audit", true);
                    map.put(REMINDER_CONFIG, true);
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
            m.put(REMINDER_CONFIG, true);
        } else {
            m.put("tasks", true);
            m.put("country", true);
            m.put("aiConfig", false);
            m.put("feishuConfig", false);
            m.put("users", false);
            m.put("audit", false);
            m.put(RECORD_CALIBRATE, false);
            m.put(REMINDER_CONFIG, false);
        }
        return m;
    }

    private static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "user";
        }
        return role.trim().toLowerCase();
    }
}
