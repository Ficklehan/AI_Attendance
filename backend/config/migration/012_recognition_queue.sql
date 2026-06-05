-- 识别公平队列（DB 持久层，配合 Redis 列表使用）
CREATE TABLE IF NOT EXISTS recognition_queue_jobs (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    config_country VARCHAR(64) NULL,
    client VARCHAR(64) NULL,
    job_source VARCHAR(32) NOT NULL COMMENT 'user|recovery|pages',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    instance_id VARCHAR(96) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    INDEX idx_rq_status_created (status, created_at),
    INDEX idx_rq_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
