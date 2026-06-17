-- 飞书多维表按国家配置（替代运行时读取 feishu.md）
CREATE TABLE IF NOT EXISTS feishu_country_config (
    country_code VARCHAR(16) NOT NULL PRIMARY KEY COMMENT 'default 或 CN/FR/NL 等',
    app_token VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Bitable App Token',
    table_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Bitable Table ID',
    field_mapping MEDIUMTEXT NOT NULL COMMENT '字段映射 JSON 数组',
    sync_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=确认后同步飞书多维表',
    seed_version INT NOT NULL DEFAULT 1 COMMENT '内置模板版本号',
    user_modified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=用户曾在配置页保存过',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞书多维表国家配置';
