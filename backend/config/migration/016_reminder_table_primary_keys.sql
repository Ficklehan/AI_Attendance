-- 修复 reminder_rule_users / reminder_feishu_messages 主键重复或定义过期问题
-- 场景：无复合主键导致重复行；feishu 表增加 locale_key 后仍使用 (user_id, rule_id) 主键

-- 1) reminder_rule_users：去重并确保 PRIMARY KEY (rule_id, user_id)
CREATE TABLE IF NOT EXISTS _reminder_rule_users_dedup_016 (
    rule_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (rule_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO _reminder_rule_users_dedup_016 (rule_id, user_id)
SELECT rule_id, user_id FROM reminder_rule_users;

DROP TABLE IF EXISTS reminder_rule_users_backup_016;
RENAME TABLE reminder_rule_users TO reminder_rule_users_backup_016;
RENAME TABLE _reminder_rule_users_dedup_016 TO reminder_rule_users;
CREATE INDEX idx_reminder_rule_users_user ON reminder_rule_users (user_id);
DROP TABLE reminder_rule_users_backup_016;

-- 2) reminder_feishu_messages：去重并确保 PRIMARY KEY (user_id, rule_id, locale_key)
-- 需已存在 locale_key 列（011_reminder_template_locales.sql 或应用自举）
UPDATE reminder_feishu_messages
SET locale_key = 'zh-CN'
WHERE locale_key IS NULL OR locale_key = '';

CREATE TABLE IF NOT EXISTS _reminder_feishu_messages_dedup_016 (
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    feishu_message_id VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, rule_id, locale_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO _reminder_feishu_messages_dedup_016 (user_id, rule_id, locale_key, feishu_message_id, updated_at)
SELECT user_id,
       rule_id,
       locale_key,
       SUBSTRING_INDEX(GROUP_CONCAT(feishu_message_id ORDER BY updated_at DESC), ',', 1) AS feishu_message_id,
       MAX(updated_at) AS updated_at
FROM reminder_feishu_messages
GROUP BY user_id, rule_id, locale_key;

DROP TABLE IF EXISTS reminder_feishu_messages_backup_016;
RENAME TABLE reminder_feishu_messages TO reminder_feishu_messages_backup_016;
RENAME TABLE _reminder_feishu_messages_dedup_016 TO reminder_feishu_messages;
DROP TABLE reminder_feishu_messages_backup_016;
