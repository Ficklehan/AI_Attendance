package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.request.AdminUserCreateRequest;
import com.attendance.dto.request.AdminUserUpdateRequest;
import com.attendance.dto.request.LoginRequest;
import com.attendance.dto.request.RegisterRequest;
import com.attendance.dto.response.LoginResponse;
import com.attendance.dto.response.UserListDTO;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.security.SecurityUtils;
import com.attendance.util.IdGenerator;
import com.attendance.util.JwtUtil;
import com.attendance.util.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PermissionService permissionService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userMapper.existsByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.USER_ALREADY_EXISTS);
        }

        if (userMapper.existsByEmail(request.getEmail()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setId(IdGenerator.generateId());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("user");
        user.setRealName(request.getRealName());
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus("active");

        userMapper.insertUser(user);
        log.info("用户注册成功: username={}", user.getUsername());

        return generateLoginResponse(user);
    }

    public LoginResponse login(LoginRequest request) {
        String loginId = request.getUsername() != null ? request.getUsername().trim() : "";
        User user = userMapper.selectUserByUsername(loginId);
        if (user == null && loginId.contains("@")) {
            user = userMapper.selectUserByEmail(loginId);
        }
        
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_NOT_FOUND);
        }

        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_DISABLED);
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, ErrorKeys.PASSWORD_WRONG);
        }

        userMapper.updateUserLastLogin(user.getId());
        log.info("用户登录成功: username={}", user.getUsername());

        return generateLoginResponse(user);
    }

    public User getUserById(String userId) {
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_NOT_FOUND);
        }
        return user;
    }

    public User getCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = getUserById(userId);
        user.setPermissions(permissionService.effectivePermissions(user));
        return user;
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = getUserById(userId);

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, ErrorKeys.OLD_PASSWORD_WRONG);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateUser(user);
        log.info("用户修改密码成功: userId={}", userId);
    }

    public User findByFeishuUserId(String feishuUserId) {
        return userMapper.selectUserByFeishuUserId(feishuUserId);
    }

    public LoginResponse loginByFeishu(User user) {
        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_DISABLED);
        }

        userMapper.updateUserLastLogin(user.getId());
        log.info("飞书用户登录成功: username={}, feishuUserId={}", user.getUsername(), user.getFeishuUserId());

        return generateLoginResponse(user);
    }

    @Transactional
    public LoginResponse registerByFeishu(String feishuUserId, String name, String email) {
        if (userMapper.existsByFeishuUserId(feishuUserId) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.FEISHU_USER_ALREADY_EXISTS);
        }

        if (email != null && userMapper.existsByEmail(email) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setId(IdGenerator.generateId());
        user.setUsername(email != null ? email.split("@")[0] : "feishu_" + feishuUserId.substring(0, 8));
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setFeishuUserId(feishuUserId);
        user.setRole("user");
        user.setRealName(name);
        user.setStatus("active");

        userMapper.insertUser(user);
        log.info("飞书用户注册成功: username={}, feishuUserId={}", user.getUsername(), feishuUserId);

        return generateLoginResponse(user);
    }

    public List<UserListDTO> listUsersForAdmin(long offset, long size) {
        return userMapper.selectUserList(offset, size).stream()
                .map(this::toUserListDto)
                .collect(Collectors.toList());
    }

    public long countUsersForAdmin() {
        return userMapper.countUser();
    }

    public UserListDTO getUserDtoForAdmin(String userId) {
        return toUserListDto(getUserById(userId));
    }

    @Transactional
    public UserListDTO createUserByAdmin(AdminUserCreateRequest request) {
        if (userMapper.existsByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.USER_ALREADY_EXISTS);
        }
        if (userMapper.existsByEmail(request.getEmail()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
        }
        String role = normalizeRole(request.getRole());
        User user = new User();
        user.setId(IdGenerator.generateId());
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setRealName(request.getRealName());
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus("active");
        userMapper.insertUser(user);
        log.info("管理员创建用户: username={}, role={}", user.getUsername(), role);
        return toUserListDto(user);
    }

    @Transactional
    public UserListDTO updateUserByAdmin(String userId, AdminUserUpdateRequest request) {
        User user = getUserById(userId);
        String currentAdminId = SecurityUtils.getCurrentUserId();
        if ("admin".equalsIgnoreCase(user.getRole())
                && request.getRole() != null
                && !"admin".equalsIgnoreCase(request.getRole())
                && userId.equals(currentAdminId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            User byEmail = userMapper.selectUserByEmail(request.getEmail().trim());
            if (byEmail != null && !byEmail.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
            }
            user.setEmail(request.getEmail().trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            user.setRole(normalizeRole(request.getRole()));
        }
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            user.setStatus(request.getStatus().trim());
        }
        if (request.getFeishuUserId() != null) {
            String feishuId = request.getFeishuUserId().trim();
            if (feishuId.isEmpty()) {
                user.setFeishuUserId(null);
            } else {
                User byFeishu = userMapper.selectUserByFeishuUserId(feishuId);
                if (byFeishu != null && !byFeishu.getId().equals(userId)) {
                    throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.FEISHU_USER_ALREADY_EXISTS);
                }
                user.setFeishuUserId(feishuId);
            }
        }
        userMapper.updateUser(user);
        log.info("管理员更新用户: userId={}, role={}", userId, user.getRole());
        return toUserListDto(getUserById(userId));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.trim().toLowerCase();
        if (!"admin".equals(normalized) && !"user".equals(normalized)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    java.util.Map.of("detail", "role must be admin or user"));
        }
        return normalized;
    }

    private UserListDTO toUserListDto(User user) {
        UserListDTO dto = new UserListDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setRealName(user.getRealName());
        dto.setEmployeeId(user.getEmployeeId());
        dto.setStatus(user.getStatus());
        dto.setFeishuUserId(user.getFeishuUserId());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    private LoginResponse generateLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setRole(user.getRole());
        userInfo.setRealName(user.getRealName());
        userInfo.setPermissions(permissionService.effectivePermissions(user));

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserInfo(userInfo);

        return response;
    }
}