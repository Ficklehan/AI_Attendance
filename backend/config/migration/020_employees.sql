-- 员工主档与 task_records 工号字段（线下序号 line_no + 系统工号 employee_no）

CREATE TABLE IF NOT EXISTS employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_no VARCHAR(16) NOT NULL COMMENT '系统工号 FR00001',
    region_code VARCHAR(8) NOT NULL COMMENT '工作地区 task.promptCountry',
    agency_key VARCHAR(255) NOT NULL COMMENT '中介机构规范化',
    match_name VARCHAR(255) NOT NULL COMMENT '发号比对姓名(默认含流水号)',
    display_name VARCHAR(255) NULL COMMENT '最近展示姓名',
    status TINYINT NOT NULL DEFAULT 1,
    first_created_at DATETIME NOT NULL,
    last_attendance_date DATE NULL,
    last_seen_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_no (emp_no),
    UNIQUE KEY uk_identity (region_code, agency_key, match_name),
    INDEX idx_region (region_code),
    INDEX idx_match_name (match_name),
    INDEX idx_last_attendance (last_attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS employee_serial_counters (
    region_code VARCHAR(8) PRIMARY KEY,
    next_seq INT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
