package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(16)
public class SystemRoleDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemRoleDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public SystemRoleDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS system_role ("
                    + "role_key VARCHAR(32) PRIMARY KEY,"
                    + "role_name VARCHAR(64) NOT NULL,"
                    + "built_in TINYINT(1) NOT NULL DEFAULT 0,"
                    + "sort_order INT NOT NULL DEFAULT 0,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            jdbcTemplate.update("INSERT IGNORE INTO system_role (role_key, role_name, built_in, sort_order) VALUES "
                    + "('admin', '管理员', 1, 0), ('user', '普通用户', 1, 10)");
            try {
                jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'user'");
            } catch (Exception ignored) {
                // 已迁移则忽略
            }
        } catch (Exception e) {
            log.warn("system_role 表初始化跳过: {}", e.getMessage());
        }
    }
}
