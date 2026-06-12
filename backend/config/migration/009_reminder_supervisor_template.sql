-- 提醒规则：非任务操作者（督办人）专用文案
ALTER TABLE reminder_rules
    ADD COLUMN message_template_supervisor TEXT NULL COMMENT '非任务创建者提醒文案' AFTER message_template;
