package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.request.AdminUserCreateRequest;
import com.attendance.dto.request.AdminUserUpdateRequest;
import com.attendance.dto.response.UserListDTO;
import com.attendance.security.AdminAuthService;
import com.attendance.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<PageResult<UserListDTO>> listUsers(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        adminAuthService.requireAdmin();
        long offset = (current - 1) * size;
        List<UserListDTO> records = userService.listUsersForAdmin(offset, size);
        long total = userService.countUsersForAdmin();
        return Result.success(PageResult.of(records, total, current, size));
    }

    @GetMapping("/{userId}")
    public Result<UserListDTO> getUser(@PathVariable String userId) {
        adminAuthService.requireAdmin();
        return Result.success(userService.getUserDtoForAdmin(userId));
    }

    @PostMapping
    public Result<UserListDTO> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        adminAuthService.requireAdmin();
        return Result.success(userService.createUserByAdmin(request));
    }

    @PutMapping("/{userId}")
    public Result<UserListDTO> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        adminAuthService.requireAdmin();
        return Result.success(userService.updateUserByAdmin(userId, request));
    }
}
