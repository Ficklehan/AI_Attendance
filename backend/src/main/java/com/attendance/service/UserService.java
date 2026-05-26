package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.dto.request.LoginRequest;
import com.attendance.dto.request.RegisterRequest;
import com.attendance.dto.response.LoginResponse;
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

@Service
public class UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userMapper.existsByUsername(request.getUsername()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        if (userMapper.existsByEmail(request.getEmail()) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被使用");
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
        User user = userMapper.selectUserByUsername(request.getUsername());
        
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户已被禁用");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "密码错误");
        }

        userMapper.updateUserLastLogin(user.getId());
        log.info("用户登录成功: username={}", user.getUsername());

        return generateLoginResponse(user);
    }

    public User getUserById(String userId) {
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public User getCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        return getUserById(userId);
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        String userId = SecurityUtils.getCurrentUserId();
        User user = getUserById(userId);

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "旧密码错误");
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
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户已被禁用");
        }

        userMapper.updateUserLastLogin(user.getId());
        log.info("飞书用户登录成功: username={}, feishuUserId={}", user.getUsername(), user.getFeishuUserId());

        return generateLoginResponse(user);
    }

    @Transactional
    public LoginResponse registerByFeishu(String feishuUserId, String name, String email) {
        if (userMapper.existsByFeishuUserId(feishuUserId) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "飞书用户已存在");
        }

        if (email != null && userMapper.existsByEmail(email) > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被使用");
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

    private LoginResponse generateLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setRole(user.getRole());
        userInfo.setRealName(user.getRealName());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserInfo(userInfo);

        return response;
    }
}