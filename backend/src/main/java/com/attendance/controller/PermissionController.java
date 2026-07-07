package com.attendance.controller;

import com.alibaba.fastjson.JSON;
import com.attendance.common.Result;
import com.attendance.dto.request.RolePermissionsUpdateRequest;
import com.attendance.dto.response.RolePermissionsBundleDTO;
import com.attendance.entity.User;
import com.attendance.security.AdminAuthService;
import com.attendance.service.PermissionService;
import com.attendance.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminAuthService adminAuthService;

    @GetMapping("/roles")
    public Result<RolePermissionsBundleDTO> getRolePermissions() {
        adminAuthService.requireAdmin();
        return Result.success(permissionService.getRolePermissionsBundle());
    }

    @PostMapping("/roles")
    public Result<Void> updateRolePermissions(@RequestBody Map<String, Object> body) {
        if (body != null && (body.containsKey("roles") || body.containsKey("byCountry"))) {
            RolePermissionsUpdateRequest request = JSON.parseObject(JSON.toJSONString(body), RolePermissionsUpdateRequest.class);
            permissionService.updateRolePermissionsBundle(request);
        } else if (body != null && !body.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Boolean>> legacy = (Map<String, Map<String, Boolean>>) (Map<?, ?>) body;
            permissionService.updateRolePermissions(legacy);
        } else {
            permissionService.updateRolePermissionsBundle(new RolePermissionsUpdateRequest());
        }
        return Result.success(null, "权限已更新");
    }

    @GetMapping("/me")
    public Result<Map<String, Boolean>> myPermissions(
            @RequestParam(required = false) String country) {
        User user = userService.getCurrentUser();
        String workingCountry = country != null && !country.trim().isEmpty()
                ? country.trim() : user.getWorkingCountry();
        Map<String, Boolean> perms = permissionService.effectivePermissions(user, workingCountry);
        return Result.success(perms);
    }
}
