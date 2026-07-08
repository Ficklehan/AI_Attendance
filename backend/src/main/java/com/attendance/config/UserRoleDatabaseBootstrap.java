package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(17)
public class UserRoleDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserRoleDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public UserRoleDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_role ("
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "role_key VARCHAR(32) NOT NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (user_id, role_key),"
                    + "INDEX idx_user_role_role_key (role_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            jdbcTemplate.update("INSERT IGNORE INTO user_role (user_id, role_key) "
                    + "SELECT id, role FROM users "
                    + "WHERE status != 'deleted' AND role IS NOT NULL AND TRIM(role) != ''");
        } catch (Exception e) {
            log.warn("user_role 表初始化跳过: {}", e.getMessage());
        }
    }
}
