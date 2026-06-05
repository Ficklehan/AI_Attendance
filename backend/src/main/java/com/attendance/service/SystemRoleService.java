package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.request.SystemRoleCreateRequest;
import com.attendance.dto.request.SystemRoleUpdateRequest;
import com.attendance.entity.SystemRole;
import com.attendance.mapper.RoleDataScopeMapper;
import com.attendance.mapper.SystemRoleMapper;
import com.attendance.security.AdminAuthService;
import com.attendance.security.DataScopeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class SystemRoleService {

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    private RoleDataScopeMapper roleDataScopeMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AdminAuthService adminAuthService;

    public List<SystemRole> listRoles() {
        adminAuthService.requireAdmin();
        return systemRoleMapper.selectAll();
    }

    public SystemRole requireRole(String roleKey) {
        SystemRole role = systemRoleMapper.selectByKey(normalizeRoleKey(roleKey));
        if (role == null) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "unknown role: " + roleKey));
        }
        return role;
    }

    public boolean roleExists(String roleKey) {
        if (roleKey == null || roleKey.trim().isEmpty()) {
            return false;
        }
        return systemRoleMapper.selectByKey(roleKey.trim().toLowerCase()) != null;
    }

    @Transactional
    public SystemRole createRole(SystemRoleCreateRequest request) {
        adminAuthService.requireAdmin();
        String roleKey = normalizeRoleKey(request.getRoleKey());
        if ("admin".equals(roleKey)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "cannot create reserved role admin"));
        }
        if (systemRoleMapper.selectByKey(roleKey) != null) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "role already exists: " + roleKey));
        }
        SystemRole role = new SystemRole();
        role.setRoleKey(roleKey);
        role.setRoleName(request.getRoleName().trim());
        role.setBuiltIn(false);
        role.setSortOrder(systemRoleMapper.maxSortOrder() + 10);
        systemRoleMapper.insert(role);
        initRoleDefaults(roleKey);
        permissionService.ensureRolePermissions(roleKey);
        return systemRoleMapper.selectByKey(roleKey);
    }

    public SystemRole updateRole(String roleKey, SystemRoleUpdateRequest request) {
        adminAuthService.requireAdmin();
        String normalized = normalizeRoleKey(roleKey);
        requireRole(normalized);
        systemRoleMapper.updateName(normalized, request.getRoleName().trim());
        return systemRoleMapper.selectByKey(normalized);
    }

    @Transactional
    public void deleteRole(String roleKey) {
        adminAuthService.requireAdmin();
        String normalized = normalizeRoleKey(roleKey);
        SystemRole role = requireRole(normalized);
        if (role.isBuiltIn()) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "built-in role cannot be deleted"));
        }
        if (systemRoleMapper.countUsersByRole(normalized) > 0) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    Collections.singletonMap("detail", "role is assigned to users"));
        }
        roleDataScopeMapper.deleteRulesByRole(normalized);
        roleDataScopeMapper.deleteScopeByRole(normalized);
        systemRoleMapper.deleteByKey(normalized);
        permissionService.removeRolePermissions(normalized);
    }

    public List<String> listRoleKeys() {
        List<SystemRole> roles = systemRoleMapper.selectAll();
        return roles.stream().map(SystemRole::getRoleKey).collect(java.util.stream.Collectors.toList());
    }

    private void initRoleDefaults(String roleKey) {
        if (roleDataScopeMapper.selectScopeType(roleKey) == null) {
            roleDataScopeMapper.upsertScopeType(roleKey, "restricted");
            roleDataScopeMapper.insertRule(roleKey, "owner_user", DataScopeContext.SELF_TOKEN);
        }
    }

    public static String normalizeRoleKey(String roleKey) {
        if (roleKey == null || roleKey.trim().isEmpty()) {
            return "user";
        }
        return roleKey.trim().toLowerCase();
    }
}
