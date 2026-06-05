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
public class RoleDataScopeDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataScopeDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public RoleDataScopeDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS role_data_scope ("
                    + "role VARCHAR(32) PRIMARY KEY,"
                    + "scope_type ENUM('all','restricted') NOT NULL DEFAULT 'restricted',"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS role_data_dimension_rule ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "role VARCHAR(32) NOT NULL,"
                    + "dimension ENUM('owner_user','country','warehouse','agency') NOT NULL,"
                    + "value VARCHAR(255) NOT NULL,"
                    + "UNIQUE KEY uk_role_dim_val (role, dimension, value),"
                    + "INDEX idx_role (role)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        } catch (Exception e) {
            log.warn("role_data_scope 表初始化跳过: {}", e.getMessage());
        }
    }
}
