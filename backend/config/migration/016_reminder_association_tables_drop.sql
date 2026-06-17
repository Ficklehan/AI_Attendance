-- 提醒关联表重建（第 1 步：删除）
-- 适用：reminder_rule_users / reminder_feishu_messages 主键与代码不一致，且表内无业务数据
-- 执行顺序：先本脚本 → 再 017_reminder_association_tables_create.sql
-- 说明：仅删除上述两表，不影响 reminder_rules、reminder_deliveries、user_notifications

DROP TABLE IF EXISTS reminder_feishu_messages;
DROP TABLE IF EXISTS reminder_rule_users;
