package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 角色数据权限表自愈：确保 role_data_scope / role_data_dimension_rule 存在，
 * 且 dimension ENUM 含 work_region（存量库若未跑 024 迁移，保存受限范围会 Data truncated → 500）。
 */
@Component
@Order(17)
public class RoleDataScopeDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataScopeDatabaseBootstrap.class);

    private static final String DIMENSION_ENUM =
            "ENUM('owner_user','country','warehouse','agency','work_region') NOT NULL";

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
                    + "dimension " + DIMENSION_ENUM + ","
                    + "value VARCHAR(255) NOT NULL,"
                    + "UNIQUE KEY uk_role_dim_val (role, dimension, value),"
                    + "INDEX idx_role (role)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            ensureWorkRegionDimension();
        } catch (Exception e) {
            log.warn("role_data_scope 表初始化跳过: {}", e.getMessage());
        }
    }

    /** 存量库自愈：dimension ENUM 缺少 work_region 时自动 ALTER。 */
    private void ensureWorkRegionDimension() {
        try {
            String columnType = jdbcTemplate.query(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'role_data_dimension_rule' "
                            + "AND COLUMN_NAME = 'dimension'",
                    rs -> rs.next() ? rs.getString(1) : null);
            if (columnType == null) {
                return;
            }
            String normalized = columnType.toLowerCase();
            if (normalized.contains("'work_region'")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE role_data_dimension_rule MODIFY COLUMN dimension "
                    + DIMENSION_ENUM + " COMMENT '业务维度'");
            log.info("role_data_dimension_rule.dimension 已补充 work_region: 原类型={}", columnType);
        } catch (Exception e) {
            log.warn("role_data_dimension_rule.dimension 自愈跳过: {}", e.getMessage());
        }
    }
}
