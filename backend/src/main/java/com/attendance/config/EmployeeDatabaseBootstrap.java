package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 员工主档表与 task_records 工号相关列。
 */
@Component
@Order(17)
public class EmployeeDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public EmployeeDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureEmployeesTables();
            ensureTaskRecordEmployeeColumns();
            log.info("employees 与 task_records 工号列已就绪");
        } catch (Exception e) {
            log.error("员工表初始化失败", e);
        }
    }

    private void ensureEmployeesTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS employees ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "emp_no VARCHAR(16) NOT NULL,"
                + "region_code VARCHAR(8) NOT NULL,"
                + "agency_key VARCHAR(255) NOT NULL,"
                + "match_name VARCHAR(255) NOT NULL,"
                + "display_name VARCHAR(255) NULL,"
                + "status TINYINT NOT NULL DEFAULT 1,"
                + "first_created_at DATETIME NOT NULL,"
                + "last_attendance_date DATE NULL,"
                + "last_seen_at DATETIME NULL,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_emp_no (emp_no),"
                + "UNIQUE KEY uk_identity (region_code, agency_key, match_name),"
                + "INDEX idx_region (region_code),"
                + "INDEX idx_match_name (match_name),"
                + "INDEX idx_last_attendance (last_attendance_date)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS employee_serial_counters ("
                + "region_code VARCHAR(8) PRIMARY KEY,"
                + "next_seq INT NOT NULL DEFAULT 1"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    private void ensureTaskRecordEmployeeColumns() {
        ensureLineNoColumn();
        addColumnIfMissing("employee_id", "BIGINT NULL COMMENT 'FK employees.id' AFTER line_no");
        addColumnIfMissing("employee_no", "VARCHAR(16) NULL COMMENT '系统工号' AFTER employee_id");
        ensureIndex("idx_employee_id", "employee_id");
    }

    private void ensureLineNoColumn() {
        if (columnExists("line_no")) {
            return;
        }
        if (columnExists("emp_no")) {
            jdbcTemplate.execute("ALTER TABLE task_records CHANGE COLUMN emp_no line_no "
                    + "VARCHAR(64) NULL COMMENT '线下表序号 NO'");
            log.info("task_records.emp_no 已重命名为 line_no");
            return;
        }
        jdbcTemplate.execute("ALTER TABLE task_records ADD COLUMN line_no VARCHAR(64) NULL "
                + "COMMENT '线下表序号 NO' AFTER image_urls");
    }

    private void addColumnIfMissing(String column, String definition) {
        if (columnExists(column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE task_records ADD COLUMN " + column + " " + definition);
    }

    private void ensureIndex(String indexName, String column) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'task_records' "
                            + "AND index_name = ?",
                    Integer.class, indexName);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE task_records ADD INDEX " + indexName + " (" + column + ")");
            }
        } catch (Exception e) {
            log.debug("索引 {} 可能已存在: {}", indexName, e.getMessage());
        }
    }

    private boolean columnExists(String column) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'task_records' "
                        + "AND column_name = ?",
                Integer.class, column);
        return cnt != null && cnt > 0;
    }
}
