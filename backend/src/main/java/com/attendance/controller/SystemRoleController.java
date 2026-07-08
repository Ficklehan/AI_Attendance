package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.request.RoleMembersUpdateRequest;
import com.attendance.dto.request.SystemRoleCreateRequest;
import com.attendance.dto.request.SystemRoleUpdateRequest;
import com.attendance.dto.response.UserListDTO;
import com.attendance.entity.SystemRole;
import com.attendance.service.SystemRoleService;
import com.attendance.service.UserService;
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

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<List<SystemRole>> listRoles() {
        return Result.success(systemRoleService.listRoles());
    }

    @PostMapping
    public Result<SystemRole> createRole(@Valid @RequestBody SystemRoleCreateRequest request) {
        return Result.success(systemRoleService.createRole(request));
    }

    @PostMapping("/{roleKey}/update")
    public Result<SystemRole> updateRole(
            @PathVariable String roleKey,
            @Valid @RequestBody SystemRoleUpdateRequest request) {
        return Result.success(systemRoleService.updateRole(roleKey, request));
    }

    @PostMapping("/{roleKey}/delete")
    public Result<Void> deleteRole(@PathVariable String roleKey) {
        systemRoleService.deleteRole(roleKey);
        return Result.success(null);
    }

    @GetMapping("/{roleKey}/members")
    public Result<PageResult<UserListDTO>> listMembers(
            @PathVariable String roleKey,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        systemRoleService.requireRole(roleKey);
        return Result.success(userService.listUsersByRoleForAdmin(roleKey, page, size, keyword));
    }

    @GetMapping("/{roleKey}/candidates")
    public Result<PageResult<UserListDTO>> listMemberCandidates(
            @PathVariable String roleKey,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        systemRoleService.requireRole(roleKey);
        return Result.success(userService.listMemberCandidatesForAdmin(roleKey, page, size, keyword));
    }

    @PostMapping("/{roleKey}/members")
    public Result<Void> addMembers(
            @PathVariable String roleKey,
            @Valid @RequestBody RoleMembersUpdateRequest request) {
        systemRoleService.requireRole(roleKey);
        userService.assignUsersToRole(roleKey, request.getUserIds());
        return Result.success(null);
    }

    @PostMapping("/{roleKey}/members/{userId}/remove")
    public Result<Void> removeMember(
            @PathVariable String roleKey,
            @PathVariable String userId) {
        systemRoleService.requireRole(roleKey);
        userService.removeUserFromRole(roleKey, userId);
        return Result.success(null);
    }
}
