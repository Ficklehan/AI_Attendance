-- 站内消息渲染变量，支持按界面语言重新生成文案
ALTER TABLE user_notifications
    ADD COLUMN content_vars TEXT NULL COMMENT '提醒渲染变量 JSON' AFTER link;
