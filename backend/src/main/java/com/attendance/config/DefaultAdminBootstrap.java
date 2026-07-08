package com.attendance.config;

import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.service.UserRoleService;
import com.attendance.util.IdGenerator;
import com.attendance.util.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 开发环境确保默认管理员可登录（修复错误的历史 password_hash）。
 */
@Component
@ConditionalOnProperty(name = "attendance.bootstrap-default-admin", havingValue = "true")
public class DefaultAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminBootstrap.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Lazy
    private UserRoleService userRoleService;

    @Value("${attendance.default-admin-username:admin}")
    private String adminUsername;

    @Value("${attendance.default-admin-password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        User admin = userMapper.selectUserByUsername(adminUsername);
        if (admin == null) {
            User user = new User();
            user.setId(IdGenerator.generateId());
            user.setUsername(adminUsername);
            user.setEmail("admin@example.com");
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setRole("admin");
            user.setRealName("系统管理员");
            user.setStatus("active");
            userMapper.insertUser(user);
            userRoleService.setUserRoles(user.getId(), java.util.Collections.singletonList("admin"));
            log.warn("已创建默认管理员账号: username={}（请尽快修改密码）", adminUsername);
            return;
        }

        boolean changed = false;
        if (admin.getPasswordHash() == null
                || !passwordEncoder.matches(adminPassword, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            changed = true;
        }
        if (!userRoleService.userHasRole(admin.getId(), "admin")) {
            userRoleService.addRoleToUser(admin.getId(), "admin");
            changed = true;
        }
        if (!"active".equals(admin.getStatus())) {
            admin.setStatus("active");
            changed = true;
        }
        if (changed) {
            userMapper.updateUser(admin);
            log.warn("已校正默认管理员账号: username={}（密码已重置为配置项 attendance.default-admin-password）", adminUsername);
        }
    }
}
