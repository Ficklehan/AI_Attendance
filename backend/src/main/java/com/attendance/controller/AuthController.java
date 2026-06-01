package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.request.ChangePasswordRequest;
import com.attendance.dto.request.LoginRequest;
import com.attendance.dto.request.RegisterRequest;
import com.attendance.dto.response.LoginResponse;
import com.attendance.entity.User;
import com.attendance.service.AuditLogService;
import com.attendance.service.UserService;
import com.attendance.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {
    
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = userService.register(request);
        auditLogService.log("USER_REGISTER", "user", response.getUserInfo().getId(), null);
        return Result.success(response);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        auditLogService.log("USER_LOGIN", "user", response.getUserInfo().getId(), null);
        return Result.success(response);
    }

    @GetMapping("/profile")
    public Result<User> getProfile() {
        User user = userService.getCurrentUser();
        user.setPasswordHash(null);
        return Result.success(user);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request.getOldPassword(), request.getNewPassword());
        auditLogService.log("CHANGE_PASSWORD", "user", null, null);
        return Result.success(null, "密码修改成功");
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success(null, "退出登录成功");
    }

    @GetMapping("/verify")
    public Result<Map<String, Boolean>> verify(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        boolean valid = false;
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            valid = jwtUtil.parseToken(token) != null && !jwtUtil.isTokenExpired(token);
        }
        
        Map<String, Boolean> result = new HashMap<>();
        result.put("valid", valid);
        return Result.success(result);
    }
}