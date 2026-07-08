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
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "role_key VARCHAR(32) NOT NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uk_user_role (user_id, role_key),"
                    + "INDEX idx_user_role_role_key (role_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            migrateCompositePrimaryKey();
            jdbcTemplate.update("INSERT IGNORE INTO user_role (user_id, role_key) "
                    + "SELECT id, role FROM users "
                    + "WHERE status != 'deleted' AND role IS NOT NULL AND TRIM(role) != ''");
        } catch (Exception e) {
            log.warn("user_role 表初始化跳过: {}", e.getMessage());
        }
    }

    /**
     * 存量库自愈：将旧版复合主键 (user_id, role_key) 迁移为自增 id 单列主键，
     * 并保留 (user_id, role_key) 复合唯一约束以维持 DB 层去重。
     */
    private void migrateCompositePrimaryKey() {
        try {
            Integer hasIdColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_role' AND COLUMN_NAME = 'id'",
                    Integer.class);
            if (hasIdColumn != null && hasIdColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE user_role "
                        + "DROP PRIMARY KEY, "
                        + "ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST, "
                        + "ADD UNIQUE KEY uk_user_role (user_id, role_key)");
                log.info("user_role 已迁移为自增 id 单列主键");
            }
        } catch (Exception e) {
            log.warn("user_role 主键结构自愈跳过: {}", e.getMessage());
        }
    }
}
