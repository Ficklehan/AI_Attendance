-- 按用户+规则+locale 记录飞书汇总提醒 message_id，用于撤回后重发
-- 全新安装请用本 DDL；若表已存在且主键错误，请执行 016（DROP）+ 017（CREATE）
CREATE TABLE IF NOT EXISTS reminder_feishu_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'locale，如 fr-FR',
    feishu_message_id VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_feishu_message (user_id, rule_id, locale_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则飞书消息状态（按 locale 分条）';
