package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures reminder tables exist (migration 007).
 */
@Component
@Order(16)
public class ReminderDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReminderDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public ReminderDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reminder_rules ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "name VARCHAR(128) NOT NULL,"
                    + "description VARCHAR(512) NULL,"
                    + "task_statuses JSON NOT NULL,"
                    + "interval_value DECIMAL(10,1) NOT NULL DEFAULT 1.0,"
                    + "interval_unit VARCHAR(16) NOT NULL DEFAULT 'day',"
                    + "message_template TEXT NOT NULL,"
                    + "include_task_creator TINYINT NOT NULL DEFAULT 1,"
                    + "enabled TINYINT NOT NULL DEFAULT 1,"
                    + "last_run_at DATETIME NULL,"
                    + "last_hit_count INT NOT NULL DEFAULT 0,"
                    + "last_sent_count INT NOT NULL DEFAULT 0,"
                    + "created_by VARCHAR(64) NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "INDEX idx_reminder_rules_enabled (enabled)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reminder_rule_users ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "rule_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "UNIQUE KEY uk_reminder_rule_user (rule_id, user_id),"
                    + "INDEX idx_reminder_rule_users_user (user_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reminder_deliveries ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "rule_id VARCHAR(64) NOT NULL,"
                    + "task_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "period_bucket VARCHAR(32) NOT NULL,"
                    + "channel_site TINYINT NOT NULL DEFAULT 0,"
                    + "channel_feishu TINYINT NOT NULL DEFAULT 0,"
                    + "feishu_status VARCHAR(32) NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uk_reminder_delivery (rule_id, task_id, user_id, period_bucket),"
                    + "INDEX idx_reminder_delivery_rule (rule_id, created_at DESC)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_notifications ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "rule_id VARCHAR(64) NULL,"
                    + "period_bucket VARCHAR(32) NULL,"
                    + "title VARCHAR(256) NOT NULL,"
                    + "body TEXT NOT NULL,"
                    + "link VARCHAR(512) NULL,"
                    + "read_at DATETIME NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "INDEX idx_user_notifications_user (user_id, created_at DESC),"
                    + "INDEX idx_user_notifications_unread (user_id, read_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reminder_feishu_messages ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "rule_id VARCHAR(64) NOT NULL,"
                    + "locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN',"
                    + "feishu_message_id VARCHAR(64) NOT NULL,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uk_reminder_feishu_message (user_id, rule_id, locale_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            log.info("reminder 相关表已就绪");
            ensureScopeColumns();
            ensureIntervalValueDecimal();
            ensureFeishuMessageIdColumn();
            ensureNotificationContentVarsColumn();
            ensureTemplateLocaleColumns();
            ensureScheduleHourColumn();
            ensureReminderSchedulesTable();
            migrateMinuteIntervalUnits();
            ensureTasksStatusEnteredIndex();
            pruneExplodedNumericDeliveries();
        } catch (Exception e) {
            log.error("创建 reminder 表失败，提醒功能不可用", e);
        }
    }

    /**
     * minute 规则历史曾写入数十万逐周期投递行；折叠为 legacy 标记并删除窗口外数字桶。
     */
    private void pruneExplodedNumericDeliveries() {
        try {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM reminder_deliveries", Long.class);
            if (total == null || total < 50_000L) {
                return;
            }
            jdbcTemplate.execute(
                    "CREATE TEMPORARY TABLE IF NOT EXISTS tmp_rd_max AS "
                            + "SELECT rule_id, task_id, user_id, "
                            + "MAX(CAST(period_bucket AS UNSIGNED)) AS max_p "
                            + "FROM reminder_deliveries "
                            + "WHERE period_bucket REGEXP '^[0-9]+$' "
                            + "GROUP BY rule_id, task_id, user_id "
                            + "HAVING max_p > 600");
            int markers = jdbcTemplate.update(
                    "INSERT IGNORE INTO reminder_deliveries ("
                            + "id, rule_id, task_id, user_id, period_bucket, "
                            + "channel_site, channel_feishu, feishu_status) "
                            + "SELECT REPLACE(UUID(),'-',''), rule_id, task_id, user_id, "
                            + "CONCAT('legacy_before_', GREATEST(1, max_p - 499)), "
                            + "1, 0, 'skipped' FROM tmp_rd_max");
            int deleted = jdbcTemplate.update(
                    "DELETE d FROM reminder_deliveries d "
                            + "INNER JOIN tmp_rd_max t "
                            + "ON d.rule_id = t.rule_id AND d.task_id = t.task_id AND d.user_id = t.user_id "
                            + "WHERE d.period_bucket REGEXP '^[0-9]+$' "
                            + "AND CAST(d.period_bucket AS UNSIGNED) < GREATEST(1, t.max_p - 499)");
            jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_rd_max");
            if (markers > 0 || deleted > 0) {
                log.info("已折叠膨胀投递记录: markers={}, deleted={}, beforeCount={}",
                        markers, deleted, total);
            }
        } catch (Exception e) {
            log.warn("折叠膨胀投递记录失败: {}", e.getMessage());
        }
    }

    /** 产品单位仅 hour/day/week；存量 minute 规则升为 hour，避免 period 爆炸。 */
    private void migrateMinuteIntervalUnits() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE reminder_rules SET interval_unit = 'hour' WHERE LOWER(interval_unit) = 'minute'");
            if (updated > 0) {
                log.info("已将 {} 条 minute 提醒规则迁移为 hour", updated);
            }
        } catch (Exception e) {
            log.warn("迁移 minute 提醒单位失败: {}", e.getMessage());
        }
    }

    private void ensureTasksStatusEnteredIndex() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tasks' "
                            + "AND index_name = 'idx_tasks_status_entered'",
                    Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE tasks ADD INDEX idx_tasks_status_entered (status, status_entered_at)");
                log.info("tasks.idx_tasks_status_entered 已创建");
            }
        } catch (Exception e) {
            log.debug("idx_tasks_status_entered 可能已存在: {}", e.getMessage());
        }
    }

    private void ensureScopeColumns() {
        try {
            addColumnIfMissing("scope_countries",
                    "JSON NULL COMMENT '适用工作国家，空=全部' AFTER task_statuses");
            addColumnIfMissing("scope_roles",
                    "JSON NULL COMMENT '适用任务创建者角色，空=全部' AFTER scope_countries");
            addColumnIfMissing("message_template_supervisor",
                    "TEXT NULL COMMENT '非任务操作者提醒文案' AFTER message_template");
        } catch (Exception e) {
            log.warn("检查/添加 reminder_rules 范围列失败: {}", e.getMessage());
        }
    }

    private void ensureIntervalValueDecimal() {
        try {
            String dataType = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reminder_rules' "
                            + "AND COLUMN_NAME = 'interval_value'",
                    String.class);
            if (dataType != null && "int".equalsIgnoreCase(dataType)) {
                jdbcTemplate.execute(
                        "ALTER TABLE reminder_rules MODIFY COLUMN interval_value DECIMAL(10,1) NOT NULL DEFAULT 1.0");
                log.info("reminder_rules.interval_value 已升级为 DECIMAL(10,1)");
            }
        } catch (Exception e) {
            log.warn("检查/升级 reminder_rules.interval_value 列失败: {}", e.getMessage());
        }
    }

    private void ensureFeishuMessageIdColumn() {
        try {
            addColumnIfMissingOnTable("user_notifications", "feishu_message_id",
                    "VARCHAR(64) NULL COMMENT '飞书消息ID，用于同周期撤回重发' AFTER link");
        } catch (Exception e) {
            log.warn("检查/添加 user_notifications.feishu_message_id 列失败: {}", e.getMessage());
        }
    }

    private void ensureNotificationContentVarsColumn() {
        try {
            addColumnIfMissingOnTable("user_notifications", "content_vars",
                    "TEXT NULL COMMENT '提醒渲染变量 JSON' AFTER link");
        } catch (Exception e) {
            log.warn("检查/添加 user_notifications.content_vars 列失败: {}", e.getMessage());
        }
    }

    private void ensureTemplateLocaleColumns() {
        try {
            addColumnIfMissing("message_template_locales",
                    "TEXT NULL COMMENT '操作者多语言文案 JSON' AFTER message_template_supervisor");
            addColumnIfMissing("message_template_supervisor_locales",
                    "TEXT NULL COMMENT '督办人多语言文案 JSON' AFTER message_template_locales");
        } catch (Exception e) {
            log.warn("检查/添加 reminder_rules 多语言文案列失败: {}", e.getMessage());
        }
    }

    private void ensureScheduleHourColumn() {
        try {
            addColumnIfMissing("schedule_hour_of_day",
                    "TINYINT NULL COMMENT '0-23，仅 day/week 生效' AFTER interval_unit");
        } catch (Exception e) {
            log.warn("检查/添加 reminder_rules.schedule_hour_of_day 列失败: {}", e.getMessage());
        }
    }

    private void ensureReminderSchedulesTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reminder_schedules ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "rule_id VARCHAR(64) NOT NULL,"
                    + "task_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "period_index BIGINT NOT NULL,"
                    + "period_bucket VARCHAR(32) NOT NULL,"
                    + "due_at DATETIME NOT NULL,"
                    + "status_entered_at DATETIME NOT NULL,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'pending',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uk_reminder_schedule (rule_id, task_id, user_id, period_bucket),"
                    + "INDEX idx_reminder_schedule_due (status, due_at),"
                    + "INDEX idx_reminder_schedule_task (task_id, status),"
                    + "INDEX idx_reminder_schedule_rule (rule_id, status)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        } catch (Exception e) {
            log.warn("检查/创建 reminder_schedules 表失败: {}", e.getMessage());
        }
    }

    private void addColumnIfMissingOnTable(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                        + "AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("{}.{} 列已添加", table, column);
        }
    }

    private void addColumnIfMissing(String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reminder_rules' "
                        + "AND COLUMN_NAME = ?",
                Integer.class,
                column);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE reminder_rules ADD COLUMN " + column + " " + definition);
            log.info("reminder_rules.{} 列已添加", column);
        }
    }
}
