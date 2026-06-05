package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 确保 task_records 表存在（migration 007）。
 */
@Component
@Order(16)
public class TaskRecordDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRecordDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public TaskRecordDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS task_records ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "task_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "row_key VARCHAR(128) NOT NULL,"
                    + "record_index INT NOT NULL DEFAULT 0,"
                    + "deleted TINYINT(1) NOT NULL DEFAULT 0,"
                    + "task_status VARCHAR(32) NOT NULL,"
                    + "file_key VARCHAR(128) NULL,"
                    + "image_urls TEXT NULL,"
                    + "emp_no VARCHAR(64) NULL,"
                    + "emp_name VARCHAR(255) NULL,"
                    + "base_name VARCHAR(255) NULL,"
                    + "country VARCHAR(64) NULL,"
                    + "country_key VARCHAR(64) NULL,"
                    + "warehouse VARCHAR(128) NULL,"
                    + "warehouse_key VARCHAR(128) NULL,"
                    + "work_date VARCHAR(32) NULL,"
                    + "agency VARCHAR(255) NULL,"
                    + "agency_key VARCHAR(255) NULL,"
                    + "shift VARCHAR(64) NULL,"
                    + "arrival VARCHAR(32) NULL,"
                    + "departure VARCHAR(32) NULL,"
                    + "pause_minutes VARCHAR(32) NULL,"
                    + "signature VARCHAR(128) NULL,"
                    + "observations TEXT NULL,"
                    + "page_num VARCHAR(32) NULL,"
                    + "task_created_at DATETIME NOT NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uk_task_row (task_id, row_key),"
                    + "INDEX idx_user_status_created (user_id, task_status, task_created_at),"
                    + "INDEX idx_task_id (task_id),"
                    + "INDEX idx_dup (base_name, work_date, country_key, warehouse_key, agency_key)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            ensureTasksIndex();
            ensureProgressRowCountColumn();
            ensureRecognitionCheckpointColumns();
            ensureSmartMarkColumn();
            log.info("task_records 表已就绪");
        } catch (Exception e) {
            log.error("创建 task_records 表失败", e);
        }
    }

    private void ensureProgressRowCountColumn() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tasks' "
                            + "AND column_name = 'progress_row_count'",
                    Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE tasks ADD COLUMN progress_row_count INT NOT NULL DEFAULT 0 "
                        + "COMMENT '识别进行中已解析行数' AFTER ai_raw_output");
            }
        } catch (Exception e) {
            log.debug("progress_row_count 列可能已存在: {}", e.getMessage());
        }
    }

    private void ensureRecognitionCheckpointColumns() {
        try {
            Integer checkpoint = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tasks' "
                            + "AND column_name = 'recognition_checkpoint'",
                    Integer.class);
            if (checkpoint == null || checkpoint == 0) {
                jdbcTemplate.execute("ALTER TABLE tasks ADD COLUMN recognition_checkpoint TEXT NULL "
                        + "COMMENT '识别断点 JSON' AFTER progress_row_count");
            }
            Integer heartbeat = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tasks' "
                            + "AND column_name = 'recognition_heartbeat_at'",
                    Integer.class);
            if (heartbeat == null || heartbeat == 0) {
                jdbcTemplate.execute("ALTER TABLE tasks ADD COLUMN recognition_heartbeat_at DATETIME NULL "
                        + "COMMENT '识别任务心跳' AFTER recognition_checkpoint");
            }
        } catch (Exception e) {
            log.debug("recognition checkpoint 列可能已存在: {}", e.getMessage());
        }
    }

    private void ensureSmartMarkColumn() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'task_records' "
                            + "AND column_name = 'smart_mark'",
                    Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE task_records ADD COLUMN smart_mark VARCHAR(255) NULL "
                        + "COMMENT '识别标记(SmartMark)' AFTER page_num");
            }
        } catch (Exception e) {
            log.debug("smart_mark 列可能已存在: {}", e.getMessage());
        }
    }

    private void ensureTasksIndex() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'tasks' "
                            + "AND index_name = 'idx_user_status_created'",
                    Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE tasks ADD INDEX idx_user_status_created (user_id, status, created_at)");
            }
        } catch (Exception e) {
            log.debug("tasks 组合索引可能已存在: {}", e.getMessage());
        }
    }
}
