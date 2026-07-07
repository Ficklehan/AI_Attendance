package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.config.CountryCatalog;
import com.attendance.dto.request.AdminUserCreateRequest;
import com.attendance.dto.request.AdminUserUpdateRequest;
import com.attendance.dto.request.LoginRequest;
import com.attendance.dto.request.RegisterRequest;
import com.attendance.dto.response.LoginResponse;
import com.attendance.dto.response.UserListDTO;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.security.SecurityUtils;
import com.attendance.util.CountryResolver;
import com.attendance.util.IdGenerator;
import com.attendance.util.JwtUtil;
import com.attendance.util.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    @Autowired
    private SystemRoleService systemRoleService;

    @Autowired
    @Lazy
    private ConfigService configService;

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

    @Transactional
    public String updateCurrentUserWorkingCountry(String country) {
        User user = getCurrentUser();
        String trimmed = country == null ? "" : country.trim();
        if (trimmed.isEmpty() || "default".equalsIgnoreCase(trimmed)) {
            user.setWorkingCountry(null);
        } else {
            user.setWorkingCountry(normalizeWorkingCountry(country));
        }
        userMapper.updateUser(user);
        String effective = resolveWorkingCountryForUser(user);
        log.info("用户更新工作地区: userId={}, request={}, effective={}", user.getId(), country, effective);
        return effective;
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

    public List<UserListDTO> listUsersForAdmin(long offset, long size, String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        return userMapper.selectUserList(offset, size, normalizedKeyword).stream()
                .map(this::toUserListDto)
                .collect(Collectors.toList());
    }

    public long countUsersForAdmin(String keyword) {
        return userMapper.countUser(normalizeSearchKeyword(keyword));
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
        user.setWorkingCountry(normalizeWorkingCountry(request.getWorkingCountry()));
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
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            User byEmail = userMapper.selectUserByEmail(request.getEmail().trim());
            if (byEmail != null && !byEmail.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
            }
            user.setEmail(request.getEmail().trim());
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            user.setRole(normalizeRole(request.getRole()));
        }
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getWorkingCountry() != null) {
            user.setWorkingCountry(normalizeWorkingCountry(request.getWorkingCountry()));
        }
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            String normalizedStatus = normalizeUserStatus(request.getStatus());
            assertCanChangeUserStatus(userId, normalizedStatus);
            user.setStatus(normalizedStatus);
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

    @Transactional
    public UserListDTO updateUserStatusByAdmin(String userId, String status) {
        User user = getUserById(userId);
        String normalizedStatus = normalizeUserStatus(status);
        assertCanChangeUserStatus(userId, normalizedStatus);
        user.setStatus(normalizedStatus);
        userMapper.updateUser(user);
        log.info("管理员更新用户状态: userId={}, status={}", userId, normalizedStatus);
        return toUserListDto(getUserById(userId));
    }

    @Transactional
    public void deleteUserByAdmin(String userId) {
        User user = getUserById(userId);
        if ("deleted".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_NOT_FOUND);
        }
        assertCanDeleteUser(user);
        userMapper.deleteUserById(userId);
        log.info("管理员删除用户: userId={}, username={}", userId, user.getUsername());
    }

    public void assertUserActive(User user) {
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, ErrorKeys.USER_DISABLED);
        }
    }

    private void assertCanChangeUserStatus(String userId, String status) {
        if (!"disabled".equals(status)) {
            return;
        }
        String currentAdminId = SecurityUtils.getCurrentUserId();
        if (userId != null && userId.equals(currentAdminId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.CANNOT_DISABLE_SELF);
        }
    }

    private void assertCanDeleteUser(User user) {
        String currentAdminId = SecurityUtils.getCurrentUserId();
        if (user.getId() != null && user.getId().equals(currentAdminId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.CANNOT_DELETE_SELF);
        }
        if ("admin".equalsIgnoreCase(user.getRole())
                && userMapper.countActiveByRole("admin") <= 1) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.CANNOT_DELETE_LAST_ADMIN);
        }
    }

    private String normalizeUserStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "active";
        }
        String normalized = status.trim().toLowerCase();
        if ("active".equals(normalized) || "disabled".equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                java.util.Collections.singletonMap("detail", "status must be active or disabled"));
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "user";
        }
        String normalized = SystemRoleService.normalizeRoleKey(role);
        if (!systemRoleService.roleExists(normalized)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    java.util.Collections.singletonMap("detail", "unknown role: " + role));
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
        String storedCountry = user.getWorkingCountry();
        if (storedCountry != null && !storedCountry.trim().isEmpty()) {
            dto.setWorkingCountry(CountryResolver.normalize(storedCountry.trim()));
        } else {
            dto.setWorkingCountry(null);
        }
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
        userInfo.setWorkingCountry(resolveWorkingCountryForUser(user));
        userInfo.setPersonalWorkingCountry(getPersonalWorkingCountry(user));
        userInfo.setPermissions(permissionService.effectivePermissions(user));

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserInfo(userInfo);

        return response;
    }

    /** 用户个人工作地区；未单独配置时返回 null（表示全局默认）。 */
    public String getPersonalWorkingCountry(User user) {
        if (user == null) {
            return null;
        }
        String personal = user.getWorkingCountry();
        if (personal == null || personal.trim().isEmpty()) {
            return null;
        }
        String normalized = CountryResolver.normalize(personal.trim());
        return "default".equalsIgnoreCase(normalized) ? null : normalized;
    }

    /** 用户有效工作地区：个人配置优先，否则系统默认。 */
    public String resolveWorkingCountryForUser(User user) {
        if (user == null) {
            return configService.getGlobalWorkingCountry();
        }
        String personal = user.getWorkingCountry();
        if (personal != null && !personal.trim().isEmpty()) {
            return CountryResolver.normalize(personal.trim());
        }
        return configService.getGlobalWorkingCountry();
    }

    public String resolveWorkingCountryForUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return configService.getGlobalWorkingCountry();
        }
        User user = userMapper.selectUserById(userId);
        return resolveWorkingCountryForUser(user);
    }

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 规范化并校验工作地区；空字符串或 null 表示清除个人配置（回退系统默认）。
     */
    private String normalizeWorkingCountry(String country) {
        if (country == null) {
            return null;
        }
        String trimmed = country.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!CountryCatalog.isSupported(trimmed)) {
            throw new BusinessException(400, "不支持的工作地区: " + trimmed);
        }
        return CountryResolver.normalize(trimmed);
    }
}