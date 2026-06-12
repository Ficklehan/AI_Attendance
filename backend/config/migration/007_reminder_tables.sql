-- 任务提醒：规则、投递去重、站内消息
CREATE TABLE IF NOT EXISTS reminder_rules (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    task_statuses JSON NOT NULL COMMENT '如 ["processed"]',
    interval_value INT NOT NULL DEFAULT 1,
    interval_unit VARCHAR(16) NOT NULL DEFAULT 'day' COMMENT 'hour|day|week',
    message_template TEXT NOT NULL,
    include_task_creator TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_run_at DATETIME NULL,
    last_hit_count INT NOT NULL DEFAULT 0,
    last_sent_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reminder_rules_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务提醒规则';

CREATE TABLE IF NOT EXISTS reminder_rule_users (
    rule_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (rule_id, user_id),
    INDEX idx_reminder_rule_users_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则-提醒人';

CREATE TABLE IF NOT EXISTS reminder_deliveries (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    period_bucket VARCHAR(32) NOT NULL,
    channel_site TINYINT NOT NULL DEFAULT 0,
    channel_feishu TINYINT NOT NULL DEFAULT 0,
    feishu_status VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_delivery (rule_id, task_id, user_id, period_bucket),
    INDEX idx_reminder_delivery_rule (rule_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒投递去重';

CREATE TABLE IF NOT EXISTS user_notifications (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NULL,
    period_bucket VARCHAR(32) NULL,
    title VARCHAR(256) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(512) NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_notifications_user (user_id, created_at DESC),
    INDEX idx_user_notifications_unread (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内消息';
