-- 已有库升级：创建提示词表（标准内容由应用启动时从内置模板写入 recognition_prompt）
USE attendance_assistant;

CREATE TABLE IF NOT EXISTS recognition_prompt (
    country_code VARCHAR(16) NOT NULL PRIMARY KEY COMMENT 'default 或 CN/FR/NL/IT 等',
    ai_prompt MEDIUMTEXT NOT NULL COMMENT '主要识别提示词',
    continue_prompt TEXT NOT NULL COMMENT '继续输出提示词',
    seed_version INT NOT NULL DEFAULT 1 COMMENT '内置模板版本号',
    user_modified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=用户曾在配置页保存过',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='识别提示词表';

INSERT INTO plugin_config (config_key, config_value, config_type, description) VALUES
('recognition_prompt_seed_version', '2', 'number', '内置识别提示词模板版本')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);

-- 执行后重启后端，将自动把 canonical 标准提示词写入 recognition_prompt 表
