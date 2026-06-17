-- 提醒关联表重建（第 2 步：创建）
-- 适用：已执行 016_reminder_association_tables_drop.sql 后的空库
-- 执行顺序：016（DROP）→ 本脚本（CREATE）→ 重启后端
--
-- 设计：单列自增 id 作主键；业务唯一性由 UNIQUE 约束保证（Mapper upsert 依赖 UNIQUE）

CREATE TABLE reminder_rule_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    rule_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_reminder_rule_user (rule_id, user_id),
    INDEX idx_reminder_rule_users_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则-提醒人';

CREATE TABLE reminder_feishu_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'locale，如 fr-FR',
    feishu_message_id VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_feishu_message (user_id, rule_id, locale_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则飞书消息状态（按 locale 分条）';
