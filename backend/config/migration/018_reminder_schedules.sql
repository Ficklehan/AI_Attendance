-- 提醒计划表：由规则配置计算 due_at，调度器仅执行到期项
CREATE TABLE IF NOT EXISTS reminder_schedules (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    period_index BIGINT NOT NULL,
    period_bucket VARCHAR(32) NOT NULL,
    due_at DATETIME NOT NULL,
    status_entered_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_schedule (rule_id, task_id, user_id, period_bucket),
    INDEX idx_reminder_schedule_due (status, due_at),
    INDEX idx_reminder_schedule_task (task_id, status),
    INDEX idx_reminder_schedule_rule (rule_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
