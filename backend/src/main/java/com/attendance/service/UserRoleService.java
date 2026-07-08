package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRoleService {

    private static final Logger log = LoggerFactory.getLogger(UserRoleService.class);
    private static final String DEFAULT_ROLE = "user";

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemRoleService systemRoleService;

    public List<String> getRoleKeysForUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.singletonList(DEFAULT_ROLE);
        }
        List<String> roles = userRoleMapper.selectRoleKeysByUserId(userId.trim());
        if (roles != null && !roles.isEmpty()) {
            return normalizeRoleList(roles);
        }
        User user = userMapper.selectUserById(userId.trim());
        if (user != null && user.getRole() != null && !user.getRole().trim().isEmpty()) {
            return Collections.singletonList(SystemRoleService.normalizeRoleKey(user.getRole()));
        }
        return Collections.singletonList(DEFAULT_ROLE);
    }

    public Map<String, List<String>> getRoleKeysByUserIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> ids = userIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String id : ids) {
            result.put(id, new ArrayList<>());
        }
        for (Map<String, Object> row : userRoleMapper.selectRoleKeysByUserIds(ids)) {
            if (row == null) {
                continue;
            }
            String userId = stringValue(row.get("userId"));
            String roleKey = stringValue(row.get("roleKey"));
            if (userId.isEmpty() || roleKey.isEmpty()) {
                continue;
            }
            result.computeIfAbsent(userId, key -> new ArrayList<>()).add(roleKey);
        }
        for (Map.Entry<String, List<String>> entry : result.entrySet()) {
            if (entry.getValue().isEmpty()) {
                User user = userMapper.selectUserById(entry.getKey());
                if (user != null && user.getRole() != null && !user.getRole().trim().isEmpty()) {
                    entry.setValue(Collections.singletonList(
                            SystemRoleService.normalizeRoleKey(user.getRole())));
                } else {
                    entry.setValue(Collections.singletonList(DEFAULT_ROLE));
                }
            } else {
                entry.setValue(normalizeRoleList(entry.getValue()));
            }
        }
        return result;
    }

    public boolean userHasRole(String userId, String roleKey) {
        if (userId == null || roleKey == null) {
            return false;
        }
        if (userRoleMapper.existsUserRole(userId, SystemRoleService.normalizeRoleKey(roleKey)) > 0) {
            return true;
        }
        return getRoleKeysForUserId(userId).contains(SystemRoleService.normalizeRoleKey(roleKey));
    }

    public boolean userHasAnyRole(String userId, Collection<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return true;
        }
        Set<String> owned = new LinkedHashSet<>(getRoleKeysForUserId(userId));
        for (String roleKey : roleKeys) {
            if (roleKey != null && owned.contains(SystemRoleService.normalizeRoleKey(roleKey))) {
                return true;
            }
        }
        return false;
    }

    public long countUsersByRole(String roleKey) {
        return userRoleMapper.countUsersByRoleKey(SystemRoleService.normalizeRoleKey(roleKey));
    }

    public long countActiveAdmins() {
        return userRoleMapper.countActiveAdmins();
    }

    @Transactional
    public void assignRoleToUsers(String roleKey, Collection<String> userIds) {
        String role = validateRoleKey(roleKey);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (String userId : userIds) {
            if (userId == null || userId.trim().isEmpty()) {
                continue;
            }
            addRoleToUser(userId.trim(), role);
        }
    }

    @Transactional
    public void addRoleToUser(String userId, String roleKey) {
        String role = validateRoleKey(roleKey);
        userRoleMapper.insertUserRole(userId, role);
        syncPrimaryRole(userId);
        log.info("用户已绑定角色: userId={}, role={}", userId, role);
    }

    @Transactional
    public void removeRoleFromUser(String userId, String roleKey) {
        String role = validateRoleKey(roleKey);
        if (DEFAULT_ROLE.equals(role)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "cannot remove default user role"));
        }
        userRoleMapper.deleteUserRole(userId, role);
        ensureDefaultRole(userId);
        syncPrimaryRole(userId);
        log.info("用户已解绑角色: userId={}, role={}", userId, role);
    }

    @Transactional
    public void setUserRoles(String userId, List<String> roleKeys) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        List<String> normalized = normalizeRequestedRoles(roleKeys);
        userRoleMapper.deleteAllRolesForUser(userId);
        for (String role : normalized) {
            userRoleMapper.insertUserRole(userId, role);
        }
        ensureDefaultRole(userId);
        syncPrimaryRole(userId);
    }

    @Transactional
    public void ensureDefaultRole(String userId) {
        if (userRoleMapper.selectRoleKeysByUserId(userId).isEmpty()) {
            userRoleMapper.insertUserRole(userId, DEFAULT_ROLE);
        } else if (userRoleMapper.existsUserRole(userId, DEFAULT_ROLE) == 0
                && userRoleMapper.selectRoleKeysByUserId(userId).size() == 0) {
            userRoleMapper.insertUserRole(userId, DEFAULT_ROLE);
        }
        List<String> roles = userRoleMapper.selectRoleKeysByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            userRoleMapper.insertUserRole(userId, DEFAULT_ROLE);
        }
    }

    @Transactional
    public void initializeUserRoles(String userId, String primaryRole) {
        String role = primaryRole != null && !primaryRole.trim().isEmpty()
                ? validateRoleKey(primaryRole) : DEFAULT_ROLE;
        userRoleMapper.insertUserRole(userId, DEFAULT_ROLE);
        if (!DEFAULT_ROLE.equals(role)) {
            userRoleMapper.insertUserRole(userId, role);
        }
        syncPrimaryRole(userId);
    }

    public String resolvePrimaryRole(List<String> roleKeys) {
        List<String> normalized = normalizeRoleList(roleKeys);
        if (normalized.contains("admin")) {
            return "admin";
        }
        for (String role : normalized) {
            if (!DEFAULT_ROLE.equals(role)) {
                return role;
            }
        }
        return DEFAULT_ROLE;
    }

    private void syncPrimaryRole(String userId) {
        List<String> roles = userRoleMapper.selectRoleKeysByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            userRoleMapper.insertUserRole(userId, DEFAULT_ROLE);
            roles = Collections.singletonList(DEFAULT_ROLE);
        }
        String primary = resolvePrimaryRole(roles);
        userMapper.updateUserRole(userId, primary);
    }

    private String validateRoleKey(String roleKey) {
        String role = SystemRoleService.normalizeRoleKey(roleKey);
        if (!systemRoleService.roleExists(role)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "unknown role: " + roleKey));
        }
        return role;
    }

    private List<String> normalizeRequestedRoles(List<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return Collections.singletonList(DEFAULT_ROLE);
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String roleKey : roleKeys) {
            if (roleKey == null || roleKey.trim().isEmpty()) {
                continue;
            }
            set.add(validateRoleKey(roleKey));
        }
        if (set.isEmpty()) {
            set.add(DEFAULT_ROLE);
        }
        return new ArrayList<>(set);
    }

    private static List<String> normalizeRoleList(List<String> roleKeys) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String roleKey : roleKeys) {
            if (roleKey != null && !roleKey.trim().isEmpty()) {
                set.add(SystemRoleService.normalizeRoleKey(roleKey));
            }
        }
        if (set.isEmpty()) {
            set.add(DEFAULT_ROLE);
        }
        return new ArrayList<>(set);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
