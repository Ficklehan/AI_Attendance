package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.PageResult;
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
import com.attendance.security.AdminAuthService;
import com.attendance.security.SecurityUtils;
import com.alibaba.fastjson.JSONObject;
import com.attendance.util.CountryResolver;
import com.attendance.util.FeishuUserProfileResolver;
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
import java.util.Map;
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
    private AdminAuthService adminAuthService;

    @Autowired
    private UserRoleService userRoleService;

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
        userRoleService.initializeUserRoles(user.getId(), "user");
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

    @Transactional
    public LoginResponse authenticateFeishuUser(JSONObject userInfo, String feishuUserId) {
        FeishuUserProfileResolver.Profile profile = FeishuUserProfileResolver.resolve(userInfo, feishuUserId);
        User user = findByFeishuUserId(feishuUserId);
        if (user != null) {
            syncFeishuProfile(user, profile);
            return loginByFeishu(user);
        }
        return registerByFeishu(feishuUserId, profile);
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
    public LoginResponse registerByFeishu(String feishuUserId, FeishuUserProfileResolver.Profile profile) {
        if (userMapper.existsByFeishuUserId(feishuUserId) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.FEISHU_USER_ALREADY_EXISTS);
        }

        String email = profile.getEmail() != null
                ? profile.getEmail()
                : FeishuUserProfileResolver.buildInternalEmail(feishuUserId);
        if (userMapper.existsByEmail(email) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setId(IdGenerator.generateId());
        user.setUsername(allocateUniqueUsername(profile.getUsername(), null));
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setFeishuUserId(feishuUserId);
        user.setRole("user");
        user.setRealName(profile.getRealName());
        user.setEmployeeId(profile.getEmployeeId());
        user.setStatus("active");

        userMapper.insertUser(user);
        userRoleService.initializeUserRoles(user.getId(), "user");
        log.info("飞书用户注册成功: username={}, feishuUserId={}", user.getUsername(), feishuUserId);

        return generateLoginResponse(user);
    }

    private void syncFeishuProfile(User user, FeishuUserProfileResolver.Profile profile) {
        boolean dirty = false;

        if (profile.getRealName() != null && !profile.getRealName().equals(user.getRealName())) {
            user.setRealName(profile.getRealName());
            dirty = true;
        }
        if (profile.getEmployeeId() != null && !profile.getEmployeeId().equals(user.getEmployeeId())) {
            user.setEmployeeId(profile.getEmployeeId());
            dirty = true;
        }

        if (profile.getEmail() != null && !profile.getEmail().equals(user.getEmail())) {
            if (canUseEmail(profile.getEmail(), user.getId())) {
                user.setEmail(profile.getEmail());
                dirty = true;
            }
        } else if (FeishuUserProfileResolver.isPlaceholderEmail(user.getEmail(), user.getFeishuUserId())) {
            String internalEmail = FeishuUserProfileResolver.buildInternalEmail(user.getFeishuUserId());
            if (!internalEmail.equals(user.getEmail()) && canUseEmail(internalEmail, user.getId())) {
                user.setEmail(internalEmail);
                dirty = true;
            }
        }

        if (FeishuUserProfileResolver.isPlaceholderUsername(user.getUsername(), user.getFeishuUserId())) {
            String nextUsername = allocateUniqueUsername(profile.getUsername(), user.getId());
            if (!nextUsername.equals(user.getUsername())) {
                user.setUsername(nextUsername);
                dirty = true;
            }
        }

        if (dirty) {
            userMapper.updateUser(user);
            log.info("飞书用户资料已同步: userId={}, username={}, email={}",
                    user.getId(), user.getUsername(), user.getEmail());
        }
    }

    private boolean canUseEmail(String email, String userId) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        if (userMapper.existsByEmail(email) == 0) {
            return true;
        }
        User existing = userMapper.selectUserByEmail(email);
        return existing != null && userId.equals(existing.getId());
    }

    private String allocateUniqueUsername(String baseUsername, String excludeUserId) {
        String candidate = baseUsername;
        int suffix = 1;
        while (isUsernameTaken(candidate, excludeUserId)) {
            candidate = baseUsername + suffix;
            suffix++;
            if (suffix > 100) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.USER_ALREADY_EXISTS);
            }
        }
        return candidate;
    }

    private boolean isUsernameTaken(String username, String excludeUserId) {
        User existing = userMapper.selectUserByUsername(username);
        if (existing == null) {
            return false;
        }
        return excludeUserId == null || !excludeUserId.equals(existing.getId());
    }

    public List<UserListDTO> listUsersForAdmin(long offset, long size, String keyword) {
        return listUsersForAdmin(offset, size, keyword, null, null);
    }

    public List<UserListDTO> listUsersForAdmin(long offset, long size, String keyword,
                                               String role, String excludeRole) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        List<User> users = userMapper.selectUserList(offset, size, normalizedKeyword, role, excludeRole);
        Map<String, List<String>> roleMap = userRoleService.getRoleKeysByUserIds(
                users.stream().map(User::getId).collect(Collectors.toList()));
        return users.stream()
                .map(user -> toUserListDto(user, roleMap.get(user.getId())))
                .collect(Collectors.toList());
    }

    public long countUsersForAdmin(String keyword) {
        return countUsersForAdmin(keyword, null, null);
    }

    public long countUsersForAdmin(String keyword, String role, String excludeRole) {
        return userMapper.countUser(normalizeSearchKeyword(keyword), role, excludeRole);
    }

    public PageResult<UserListDTO> listUsersByRoleForAdmin(String roleKey, long page, long size, String keyword) {
        adminAuthService.requireAdmin();
        String role = normalizeRole(roleKey);
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (safePage - 1) * safeSize;
        List<UserListDTO> records = listUsersForAdmin(offset, safeSize, keyword, role, null);
        long total = countUsersForAdmin(keyword, role, null);
        return PageResult.of(records, total, safePage, safeSize);
    }

    public PageResult<UserListDTO> listMemberCandidatesForAdmin(String roleKey, long page, long size, String keyword) {
        adminAuthService.requireAdmin();
        String role = normalizeRole(roleKey);
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (safePage - 1) * safeSize;
        List<UserListDTO> records = listUsersForAdmin(offset, safeSize, keyword, null, role);
        long total = countUsersForAdmin(keyword, null, role);
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Transactional
    public void assignUsersToRole(String roleKey, List<String> userIds) {
        adminAuthService.requireAdmin();
        String role = normalizeRole(roleKey);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String currentAdminId = SecurityUtils.getCurrentUserId();
        for (String userId : userIds) {
            if (userId == null || userId.trim().isEmpty()) {
                continue;
            }
            User user = getUserById(userId.trim());
            if ("deleted".equals(user.getStatus())) {
                continue;
            }
            if (userRoleService.userHasRole(userId.trim(), "admin")
                    && !"admin".equals(role)
                    && userId.trim().equals(currentAdminId)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
            }
        }
        userRoleService.assignRoleToUsers(role, userIds);
    }

    @Transactional
    public void removeUserFromRole(String roleKey, String userId) {
        adminAuthService.requireAdmin();
        String role = normalizeRole(roleKey);
        if (!userRoleService.userHasRole(userId, role)) {
            return;
        }
        String currentAdminId = SecurityUtils.getCurrentUserId();
        if ("admin".equals(role)) {
            if (userId.equals(currentAdminId)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
            }
            if (userRoleService.countActiveAdmins() <= 1) {
                throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                        java.util.Collections.singletonMap("detail", "cannot remove the last admin"));
            }
        }
        userRoleService.removeRoleFromUser(userId, role);
    }

    public UserListDTO getUserDtoForAdmin(String userId) {
        User user = getUserById(userId);
        return toUserListDto(user, userRoleService.getRoleKeysForUserId(userId));
    }

    @Transactional
    public UserListDTO createUserByAdmin(AdminUserCreateRequest request) {
        if (userMapper.existsByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.USER_ALREADY_EXISTS);
        }
        String username = request.getUsername().trim();
        String storedEmail = resolveAdminStoredEmail(request.getEmail(), username);
        if (userMapper.existsByEmail(storedEmail) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
        }
        String role = normalizeRole(request.getRole());
        User user = new User();
        user.setId(IdGenerator.generateId());
        user.setUsername(username);
        user.setEmail(storedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setRealName(request.getRealName().trim());
        user.setEmployeeId(request.getEmployeeId());
        user.setWorkingCountry(normalizeWorkingCountry(request.getWorkingCountry()));
        user.setStatus("active");
        userMapper.insertUser(user);
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            userRoleService.setUserRoles(user.getId(), request.getRoles());
        } else {
            userRoleService.initializeUserRoles(user.getId(), role);
        }
        log.info("管理员创建用户: username={}, role={}", user.getUsername(), role);
        return getUserDtoForAdmin(user.getId());
    }

    @Transactional
    public UserListDTO updateUserByAdmin(String userId, AdminUserUpdateRequest request) {
        User user = getUserById(userId);
        String currentAdminId = SecurityUtils.getCurrentUserId();
        if (request.getRoles() != null) {
            if (userRoleService.userHasRole(userId, "admin")
                    && !request.getRoles().contains("admin")
                    && userId.equals(currentAdminId)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
            }
            userRoleService.setUserRoles(userId, request.getRoles());
            user.setRole(userRoleService.resolvePrimaryRole(request.getRoles()));
        } else if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            if (userRoleService.userHasRole(userId, "admin")
                    && !"admin".equalsIgnoreCase(request.getRole())
                    && userId.equals(currentAdminId)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
            }
            userRoleService.setUserRoles(userId, java.util.Collections.singletonList(normalizeRole(request.getRole())));
            user.setRole(normalizeRole(request.getRole()));
        }
        if (request.getEmail() != null) {
            String storedEmail = resolveAdminStoredEmail(request.getEmail(), user.getUsername());
            if (!storedEmail.equals(user.getEmail())) {
                User byEmail = userMapper.selectUserByEmail(storedEmail);
                if (byEmail != null && !byEmail.getId().equals(userId)) {
                    throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, ErrorKeys.EMAIL_ALREADY_EXISTS);
                }
                user.setEmail(storedEmail);
            }
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName().trim());
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
        return getUserDtoForAdmin(userId);
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
        if (userRoleService.userHasRole(user.getId(), "admin")
                && userRoleService.countActiveAdmins() <= 1) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.CANNOT_DELETE_LAST_ADMIN);
        }
    }

    private String resolveAdminStoredEmail(String emailInput, String username) {
        String trimmed = emailInput != null ? emailInput.trim() : "";
        if (trimmed.isEmpty()) {
            return FeishuUserProfileResolver.buildLocalEmail(username);
        }
        if (!FeishuUserProfileResolver.isValidEmailFormat(trimmed)) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED,
                    java.util.Collections.singletonMap("detail", "invalid email format"));
        }
        return trimmed;
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
        return toUserListDto(user, userRoleService.getRoleKeysForUserId(user.getId()));
    }

    private UserListDTO toUserListDto(User user, List<String> roles) {
        UserListDTO dto = new UserListDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(FeishuUserProfileResolver.displayEmail(user.getEmail(), user.getFeishuUserId()));
        List<String> roleList = roles != null ? roles : userRoleService.getRoleKeysForUserId(user.getId());
        dto.setRoles(roleList);
        dto.setRole(userRoleService.resolvePrimaryRole(roleList));
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
        userInfo.setEmail(FeishuUserProfileResolver.displayEmail(user.getEmail(), user.getFeishuUserId()));
        List<String> roles = userRoleService.getRoleKeysForUserId(user.getId());
        userInfo.setRoles(roles);
        userInfo.setRole(userRoleService.resolvePrimaryRole(roles));
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