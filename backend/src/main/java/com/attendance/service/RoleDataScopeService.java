package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.request.RoleDataScopeUpdateRequest;
import com.attendance.dto.response.DimensionOptionDTO;
import com.attendance.dto.response.RoleDataScopeDTO;
import com.attendance.mapper.RoleDataScopeMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.security.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoleDataScopeService {

    private static final List<String> DIMENSIONS = Arrays.asList(
            "owner_user", "country", "warehouse", "agency");

    @Autowired
    private RoleDataScopeMapper roleDataScopeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private SystemRoleService systemRoleService;

    public Map<String, RoleDataScopeDTO> getAllRoleScopes() {
        adminAuthService.requireAdmin();
        Map<String, RoleDataScopeDTO> result = new LinkedHashMap<>();
        for (String role : systemRoleService.listRoleKeys()) {
            result.put(role, loadRoleScope(role));
        }
        return result;
    }

    public RoleDataScopeDTO getRoleScope(String role) {
        adminAuthService.requireAdmin();
        return loadRoleScope(normalizeRole(role));
    }

    @Transactional
    public RoleDataScopeDTO updateRoleScope(String role, RoleDataScopeUpdateRequest request) {
        adminAuthService.requireAdmin();
        String normalized = normalizeRole(role);
        if ("admin".equals(normalized)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    java.util.Collections.singletonMap("detail", "admin role data scope is always all"));
        }

        String scopeType = request != null && request.getScopeType() != null
                ? request.getScopeType().trim().toLowerCase() : "restricted";
        if (!"all".equals(scopeType) && !"restricted".equals(scopeType)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    java.util.Collections.singletonMap("detail", "scopeType must be all or restricted"));
        }
        if ("all".equals(scopeType)) {
            roleDataScopeMapper.upsertScopeType(normalized, "all");
            roleDataScopeMapper.deleteRulesByRole(normalized);
            return loadRoleScope(normalized);
        }

        roleDataScopeMapper.upsertScopeType(normalized, "restricted");
        roleDataScopeMapper.deleteRulesByRole(normalized);
        Map<String, List<String>> rules = request != null ? request.getRules() : null;
        if (rules != null) {
            for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
                String dimension = normalizeDimension(entry.getKey());
                if (dimension == null || entry.getValue() == null) {
                    continue;
                }
                for (String raw : entry.getValue()) {
                    if (raw == null || raw.trim().isEmpty()) {
                        continue;
                    }
                    String value = raw.trim();
                    if ("owner_user".equals(dimension) && !"__self__".equals(value)) {
                        if (userMapper.selectUserById(value) == null) {
                            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                                    java.util.Collections.singletonMap("detail", "invalid owner_user: " + value));
                        }
                    }
                    roleDataScopeMapper.insertRule(normalized, dimension, value);
                }
            }
        }
        return loadRoleScope(normalized);
    }

    public Map<String, List<DimensionOptionDTO>> getDimensionOptions() {
        adminAuthService.requireAdmin();
        Map<String, List<DimensionOptionDTO>> options = new LinkedHashMap<>();
        options.put("country", toDimensionOptions(roleDataScopeMapper.selectDistinctCountryOptions()));
        options.put("warehouse", toDimensionOptions(roleDataScopeMapper.selectDistinctWarehouseOptions()));
        options.put("agency", toDimensionOptions(roleDataScopeMapper.selectDistinctAgencyOptions()));
        options.put("owner_user", userMapper.selectUserList(0, 500, null).stream()
                .map(u -> new DimensionOptionDTO(u.getId(),
                        buildOwnerUserLabel(u.getRealName(), u.getUsername(), u.getEmail(), u.getId())))
                .collect(Collectors.toList()));
        return options;
    }

    private RoleDataScopeDTO loadRoleScope(String role) {
        RoleDataScopeDTO dto = new RoleDataScopeDTO();
        dto.setRole(role);
        boolean isAdmin = "admin".equals(role);
        dto.setEditable(!isAdmin);
        String scopeType = roleDataScopeMapper.selectScopeType(role);
        dto.setScopeType(isAdmin ? "all" : (scopeType != null ? scopeType : "restricted"));

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String dim : DIMENSIONS) {
            grouped.put(dim, new ArrayList<>());
        }
        if (!isAdmin && scopeType != null && "restricted".equalsIgnoreCase(scopeType)) {
            for (Map<String, String> row : roleDataScopeMapper.selectRulesByRole(role)) {
                if (row == null) {
                    continue;
                }
                String dimension = row.get("dimension");
                String value = row.get("value");
                if (dimension != null && value != null && grouped.containsKey(dimension)) {
                    grouped.get(dimension).add(value);
                }
            }
        }
        dto.setRules(grouped);
        return dto;
    }

    private static List<DimensionOptionDTO> toDimensionOptions(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<DimensionOptionDTO> list = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (row == null) {
                continue;
            }
            String value = trimToNull(row.get("value"));
            if (value == null) {
                continue;
            }
            String label = trimToNull(row.get("label"));
            list.add(new DimensionOptionDTO(value, label != null ? label : value));
        }
        return list;
    }

    private static String buildOwnerUserLabel(String realName, String username, String email, String id) {
        String name = trimToNull(realName);
        if (name == null) {
            name = trimToNull(username);
        }
        if (name == null) {
            name = trimToNull(email);
        }
        if (name == null) {
            return id;
        }
        return name + " (" + id + ")";
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRole(String role) {
        String r = SystemRoleService.normalizeRoleKey(role);
        if (!systemRoleService.roleExists(r)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    java.util.Collections.singletonMap("detail", "unsupported role: " + role));
        }
        return r;
    }

    private static String normalizeDimension(String dimension) {
        if (dimension == null) {
            return null;
        }
        String d = dimension.trim().toLowerCase();
        return DIMENSIONS.contains(d) ? d : null;
    }
}
