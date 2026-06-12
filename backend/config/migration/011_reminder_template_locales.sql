-- 提醒文案多语言（按工作国家 locale 推送）
ALTER TABLE reminder_rules
    ADD COLUMN message_template_locales TEXT NULL COMMENT '操作者多语言文案 JSON' AFTER message_template_supervisor,
    ADD COLUMN message_template_supervisor_locales TEXT NULL COMMENT '督办人多语言文案 JSON' AFTER message_template_locales;

ALTER TABLE reminder_feishu_messages
    ADD COLUMN locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'locale，如 fr-FR' AFTER rule_id;

ALTER TABLE reminder_feishu_messages
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id, rule_id, locale_key);
