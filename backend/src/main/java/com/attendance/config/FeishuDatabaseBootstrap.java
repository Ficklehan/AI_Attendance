package com.attendance.config;

import com.attendance.config.ConfigPathResolver;
import com.attendance.service.FeishuCountryConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动时将 canonical feishu.md 播种到数据库；运行时只读数据库。
 */
@Component
@Order(21)
public class FeishuDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeishuDatabaseBootstrap.class);

    @Autowired
    private FeishuCountryConfigService feishuCountryConfigService;

    @Autowired
    private PromptProperties promptProperties;

    @Autowired
    private ConfigPathResolver configPathResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureTableExists();
            if (!promptProperties.isSeedOnStartup()) {
                log.info("attendance.prompt.seed-on-startup=false，跳过飞书配置数据库播种");
                return;
            }
            long rows = feishuCountryConfigService.countRows();
            if (rows == 0 && promptProperties.isImportMarkdownWhenEmpty()) {
                try {
                    Path path = configPathResolver.resolveFile("feishu.md");
                    if (Files.exists(path)) {
                        String md = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        int imported = feishuCountryConfigService.importFromMarkdownContent(md, true);
                        log.info("飞书配置表为空，已从 base-config/feishu.md 导入 {} 个国家", imported);
                        rows = feishuCountryConfigService.countRows();
                    }
                } catch (Exception e) {
                    log.warn("从 feishu.md 导入失败，将使用内置 canonical 模板", e);
                }
            }
            if (rows == 0 || promptProperties.isForceSeedOnStartup()) {
                int seeded = feishuCountryConfigService.seedFromCanonical(rows == 0 || promptProperties.isForceSeedOnStartup());
                log.info("飞书配置数据库播种: rows={}, seeded={}", rows, seeded);
            } else {
                feishuCountryConfigService.seedFromCanonical(false);
                log.info("飞书配置数据库已存在，仅补全缺失国家（不覆盖用户自定义）");
            }
        } catch (Exception e) {
            log.error("飞书配置数据库播种失败", e);
        }
    }

    private void ensureTableExists() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS feishu_country_config ("
                + "country_code VARCHAR(16) NOT NULL PRIMARY KEY,"
                + "app_token VARCHAR(128) NOT NULL DEFAULT '',"
                + "table_id VARCHAR(64) NOT NULL DEFAULT '',"
                + "field_mapping MEDIUMTEXT NOT NULL,"
                + "sync_enabled TINYINT(1) NOT NULL DEFAULT 1,"
                + "seed_version INT NOT NULL DEFAULT 1,"
                + "user_modified TINYINT(1) NOT NULL DEFAULT 0,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // 兼容历史版本：如果表已存在但字段缺失，则补全列，避免保存时 Unknown column。
        ensureColumnExists("feishu_country_config", "app_token",
                "ADD COLUMN app_token VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumnExists("feishu_country_config", "table_id",
                "ADD COLUMN table_id VARCHAR(64) NOT NULL DEFAULT ''");
        ensureColumnExists("feishu_country_config", "field_mapping",
                "ADD COLUMN field_mapping MEDIUMTEXT NOT NULL");
        ensureColumnExists("feishu_country_config", "sync_enabled",
                "ADD COLUMN sync_enabled TINYINT(1) NOT NULL DEFAULT 1");
        ensureColumnExists("feishu_country_config", "seed_version",
                "ADD COLUMN seed_version INT NOT NULL DEFAULT 1");
        ensureColumnExists("feishu_country_config", "user_modified",
                "ADD COLUMN user_modified TINYINT(1) NOT NULL DEFAULT 0");
    }

    private void ensureColumnExists(String tableName, String columnName, String alterSql) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " " + alterSql);
            log.info("已补全字段: {}.{}", tableName, columnName);
        } catch (Exception e) {
            // 主要忽略：字段已存在时的异常
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("duplicate column")
                    || msg.contains("already exists")
                    || msg.contains("exists")) {
                return;
            }
            throw e;
        }
    }
}
