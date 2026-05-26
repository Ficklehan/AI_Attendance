-- 创建数据库
CREATE DATABASE IF NOT EXISTS attendance_assistant 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE attendance_assistant;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    password_hash VARCHAR(255) COMMENT '密码哈希',
    feishu_user_id VARCHAR(64) COMMENT '飞书用户ID',
    role ENUM('admin', 'user') DEFAULT 'user' COMMENT '角色',
    real_name VARCHAR(100) COMMENT '真实姓名',
    employee_id VARCHAR(50) COMMENT '员工编号',
    status ENUM('active', 'inactive', 'deleted') DEFAULT 'active' COMMENT '状态',
    last_login_at DATETIME COMMENT '最后登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX idx_email (email),
    UNIQUE INDEX idx_feishu_user_id (feishu_user_id),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 任务表
CREATE TABLE IF NOT EXISTS tasks (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '创建者用户ID',
    file_key VARCHAR(128) NOT NULL COMMENT '原始文件key或JSON数组',
    status ENUM('processing', 'processed', 'confirmed', 'failed', 'cancelled') NOT NULL DEFAULT 'processing' COMMENT '任务状态',
    raw_data JSON NULL COMMENT 'AI解析的原始数据',
    confirmed_data JSON NULL COMMENT '用户确认后的数据',
    ai_raw_output TEXT NULL COMMENT 'AI原始输出（用于调试）',
    processed_by VARCHAR(512) NULL COMMENT '处理人信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 配置表
CREATE TABLE IF NOT EXISTS plugin_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    config_type ENUM('string', 'json', 'number', 'boolean') DEFAULT 'string' COMMENT '配置类型',
    description TEXT COMMENT '配置描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '操作用户ID',
    username VARCHAR(128) NOT NULL COMMENT '操作用户名',
    action VARCHAR(50) NOT NULL COMMENT '操作类型',
    target_type VARCHAR(50) COMMENT '目标类型',
    target_id VARCHAR(64) COMMENT '目标ID',
    details JSON COMMENT '操作详情',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- 日志表
CREATE TABLE IF NOT EXISTS logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NULL COMMENT '关联任务ID',
    log_type VARCHAR(32) NOT NULL COMMENT '日志类型',
    content TEXT NOT NULL COMMENT '日志内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_log_type (log_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志表';

-- 初始化管理员账号 (密码: admin123)
INSERT INTO users (id, username, email, password_hash, role, real_name, status)
VALUES (
    'admin001',
    'admin',
    'admin@example.com',
    '$2a$10$VOkM/GlVk1PZ7MaV7SsHuuzVHSayFTKr23LTDv2uemse3wCDmatzu',
    'admin',
    '系统管理员',
    'active'
) ON DUPLICATE KEY UPDATE username=username;

-- 初始化默认配置
INSERT INTO plugin_config (config_key, config_value, config_type, description) VALUES
('feishu_bitable_app_token_DEFAULT', '', 'string', '默认多维表格APP Token'),
('feishu_bitable_table_id_DEFAULT', '', 'string', '默认多维表格Table ID'),
('feishu_field_mapping_DEFAULT', '[
    {"aiField":"NO","feishuField":"NO","type":"string","required":true},
    {"aiField":"NOM_PRENOM","feishuField":"NOM PRENOM","type":"string","required":false},
    {"aiField":"AGENCE_INTERIMAIRE","feishuField":"AGENCE INTERIMAIRE","type":"string","required":false},
    {"aiField":"HORAIRES_DU_TRAVAIL","feishuField":"HORAIRES DU TRAVAIL","type":"string","required":false},
    {"aiField":"Date","feishuField":"Date","type":"date","required":true},
    {"aiField":"ARRIVEE","feishuField":"ARRIVE","type":"string","required":false},
    {"aiField":"DEPAR","feishuField":"DEPAR","type":"string","required":false},
    {"aiField":"PAUSE","feishuField":"PAUS","type":"number","required":true},
    {"aiField":"CHECKER","feishuField":"CHECKER","type":"string","required":false},
    {"aiField":"SmartMark","feishuField":"Mark","type":"string","required":false}
]', 'json', '默认字段映射配置'),
('recognition_batch_size', '100', 'number', '识别批次大小'),
('auto_confirm', 'false', 'boolean', '是否自动确认'),
('notification_enabled', 'true', 'boolean', '是否启用通知')
ON DUPLICATE KEY UPDATE config_value=config_value;