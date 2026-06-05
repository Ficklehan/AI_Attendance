package com.attendance;

import com.attendance.dto.request.LoginRequest;
import com.attendance.dto.response.LoginResponse;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.service.PermissionService;
import com.attendance.service.UserService;
import com.attendance.util.JwtUtil;
import com.attendance.util.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user001");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("$2a$10$encodedPassword");
        testUser.setRole("user");
        testUser.setStatus("active");
    }

    @Test
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        when(userMapper.selectUserByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("password123", "$2a$10$encodedPassword")).thenReturn(true);
        when(userMapper.updateUserLastLogin(anyString())).thenReturn(1);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("test.jwt.token");

        LoginResponse result = userService.login(request);

        assertNotNull(result);
        assertEquals("test.jwt.token", result.getToken());
        assertNotNull(result.getUserInfo());
        assertEquals("testuser", result.getUserInfo().getUsername());
        
        verify(userMapper).updateUserLastLogin("user001");
    }

    @Test
    void testLoginUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password");

        when(userMapper.selectUserByUsername("nonexistent")).thenReturn(null);

        assertThrows(com.attendance.common.BusinessException.class, () -> {
            userService.login(request);
        });
    }

    @Test
    void testLoginWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpassword");

        when(userMapper.selectUserByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.matches("wrongpassword", "$2a$10$encodedPassword")).thenReturn(false);

        assertThrows(com.attendance.common.BusinessException.class, () -> {
            userService.login(request);
        });
    }

    @Test
    void testLoginDisabledUser() {
        testUser.setStatus("disabled");

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        when(userMapper.selectUserByUsername("testuser")).thenReturn(testUser);

        com.attendance.common.BusinessException ex = assertThrows(
                com.attendance.common.BusinessException.class,
                () -> userService.login(request));
        assertEquals(com.attendance.common.ErrorKeys.USER_DISABLED, ex.getMessageKey());
    }

    @Test
    void testLoginByFeishuDisabledUser() {
        testUser.setStatus("disabled");

        com.attendance.common.BusinessException ex = assertThrows(
                com.attendance.common.BusinessException.class,
                () -> userService.loginByFeishu(testUser));
        assertEquals(com.attendance.common.ErrorKeys.USER_DISABLED, ex.getMessageKey());
    }

    @Test
    void testGetUserById() {
        when(userMapper.selectUserById("user001")).thenReturn(testUser);

        User result = userService.getUserById("user001");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userMapper).selectUserById("user001");
    }

    @Test
    void testGetUserByIdNotFound() {
        when(userMapper.selectUserById("nonexistent")).thenReturn(null);

        assertThrows(com.attendance.common.BusinessException.class, () -> {
            userService.getUserById("nonexistent");
        });
    }
}
