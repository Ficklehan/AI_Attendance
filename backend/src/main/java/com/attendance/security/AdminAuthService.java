package com.attendance.security;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    @Autowired
    private UserMapper userMapper;

    public void requireAdmin() {
        if (!isCurrentUserAdmin()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ADMIN_REQUIRED);
        }
    }

    public boolean isCurrentUserAdmin() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return isAdmin(userId);
    }

    public boolean isAdmin(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        User user = userMapper.selectUserById(userId);
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }
}
