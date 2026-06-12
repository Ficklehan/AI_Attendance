-- 站内消息关联飞书 message_id，同周期提醒撤回后重发
-- 若列已存在可忽略报错；应用启动时 ReminderDatabaseBootstrap 也会自动补齐
ALTER TABLE user_notifications
    ADD COLUMN feishu_message_id VARCHAR(64) NULL COMMENT '飞书消息ID，用于同周期撤回重发' AFTER link;
