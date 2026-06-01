package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures {@code export_jobs} exists for async CSV/XLSX exports (migration 005).
 */
@Component
@Order(15)
public class ExportJobDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportJobDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public ExportJobDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS export_jobs ("
                    + "id VARCHAR(64) PRIMARY KEY COMMENT '导出任务ID',"
                    + "user_id VARCHAR(64) NOT NULL COMMENT '发起用户',"
                    + "export_type VARCHAR(32) NOT NULL COMMENT 'task_list | employee_records',"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|running|completed|failed',"
                    + "query_json TEXT COMMENT '导出筛选条件 JSON',"
                    + "file_name VARCHAR(255) COMMENT '下载文件名',"
                    + "file_path VARCHAR(512) COMMENT '服务器文件路径',"
                    + "row_count BIGINT NOT NULL DEFAULT 0 COMMENT '导出行数',"
                    + "error_message VARCHAR(1024) COMMENT '失败原因',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "completed_at DATETIME NULL,"
                    + "expires_at DATETIME NULL,"
                    + "dismissed_at DATETIME NULL COMMENT '用户从导出中心清空的时间',"
                    + "INDEX idx_export_user_created (user_id, created_at DESC),"
                    + "INDEX idx_export_status (status)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步导出任务'");
            ensureDismissedColumn();
            log.info("export_jobs 表已就绪");
        } catch (Exception e) {
            log.error("创建 export_jobs 表失败，导出功能不可用", e);
        }
    }

    private void ensureDismissedColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'export_jobs' "
                            + "AND COLUMN_NAME = 'dismissed_at'",
                    Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE export_jobs ADD COLUMN dismissed_at DATETIME NULL "
                                + "COMMENT '用户从导出中心清空的时间' AFTER expires_at");
                log.info("export_jobs.dismissed_at 列已添加");
            }
        } catch (Exception e) {
            log.warn("检查/添加 export_jobs.dismissed_at 列失败: {}", e.getMessage());
        }
    }
}
