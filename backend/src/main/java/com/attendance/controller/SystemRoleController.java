package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.request.SystemRoleCreateRequest;
import com.attendance.dto.request.SystemRoleUpdateRequest;
import com.attendance.entity.SystemRole;
import com.attendance.service.SystemRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/roles")
@Validated
public class SystemRoleController {

    @Autowired
    private SystemRoleService systemRoleService;

    @GetMapping
    public Result<List<SystemRole>> listRoles() {
        return Result.success(systemRoleService.listRoles());
    }

    @PostMapping
    public Result<SystemRole> createRole(@Valid @RequestBody SystemRoleCreateRequest request) {
        return Result.success(systemRoleService.createRole(request));
    }

    @PutMapping("/{roleKey}")
    public Result<SystemRole> updateRole(
            @PathVariable String roleKey,
            @Valid @RequestBody SystemRoleUpdateRequest request) {
        return Result.success(systemRoleService.updateRole(roleKey, request));
    }

    @DeleteMapping("/{roleKey}")
    public Result<Void> deleteRole(@PathVariable String roleKey) {
        systemRoleService.deleteRole(roleKey);
        return Result.success(null);
    }
}
