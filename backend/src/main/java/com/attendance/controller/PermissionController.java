package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.entity.User;
import com.attendance.security.AdminAuthService;
import com.attendance.service.PermissionService;
import com.attendance.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    public Result<Map<String, Map<String, Boolean>>> getRolePermissions() {
        adminAuthService.requireAdmin();
        return Result.success(permissionService.getRolePermissions());
    }

    @PutMapping("/roles")
    public Result<Void> updateRolePermissions(@RequestBody Map<String, Map<String, Boolean>> body) {
        permissionService.updateRolePermissions(body);
        return Result.success(null, "权限已更新");
    }

    @GetMapping("/me")
    public Result<Map<String, Boolean>> myPermissions() {
        User user = userService.getCurrentUser();
        return Result.success(permissionService.effectivePermissions(user));
    }
}
