-- 按用户+规则+语言记录飞书汇总提醒 message_id，用于撤回后重发
CREATE TABLE IF NOT EXISTS reminder_feishu_messages (
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'locale，如 fr-FR',
    feishu_message_id VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, rule_id, locale_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则飞书消息状态';
