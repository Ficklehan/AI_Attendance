-- 异步导出任务
CREATE TABLE IF NOT EXISTS export_jobs (
    id VARCHAR(64) PRIMARY KEY COMMENT '导出任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '发起用户',
    export_type VARCHAR(32) NOT NULL COMMENT 'task_list | employee_records',
    status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|running|completed|failed',
    query_json TEXT COMMENT '导出筛选条件 JSON',
    file_name VARCHAR(255) COMMENT '下载文件名',
    file_path VARCHAR(512) COMMENT '服务器文件路径',
    row_count BIGINT NOT NULL DEFAULT 0 COMMENT '导出行数',
    error_message VARCHAR(1024) COMMENT '失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    expires_at DATETIME NULL,
    INDEX idx_export_user_created (user_id, created_at DESC),
    INDEX idx_export_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步导出任务';
