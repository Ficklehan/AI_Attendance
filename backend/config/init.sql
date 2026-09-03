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
    role VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT '主角色标识（兼容）；多角色见 user_role',
    real_name VARCHAR(100) COMMENT '真实姓名',
    employee_id VARCHAR(50) COMMENT '员工编号',
    working_country VARCHAR(64) COMMENT '工作地区代码，如 CN、FR；default 或空表示系统默认',
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
    status_entered_at DATETIME NULL COMMENT '进入当前 status 的时间（提醒周期锚点）',
    sync_status VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT '飞书同步: none|pending|synced|sync_failed',
    sync_error VARCHAR(512) NULL COMMENT '最近一次飞书同步错误',
    prompt_country VARCHAR(64) NULL COMMENT '识别时快照的国家配置',
    raw_data JSON NULL COMMENT 'AI解析的原始数据',
    confirmed_data JSON NULL COMMENT '用户确认后的数据',
    image_urls JSON NULL COMMENT '多图 URL 列表',
    anomaly_summary JSON NULL COMMENT '异常摘要/识别跟踪',
    ai_raw_output TEXT NULL COMMENT 'AI原始输出（用于调试）',
    progress_row_count INT NOT NULL DEFAULT 0 COMMENT '识别进行中已解析行数',
    recognition_checkpoint TEXT NULL COMMENT '识别断点 JSON',
    recognition_heartbeat_at DATETIME NULL COMMENT '识别任务心跳',
    processed_by VARCHAR(512) NULL COMMENT '处理人信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_sync_status (sync_status),
    INDEX idx_created_at (created_at),
    INDEX idx_user_status_created (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 任务员工记录行级表（B+ 列表/导出/重名检测）
CREATE TABLE IF NOT EXISTS task_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '行ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '任务所属用户',
    row_key VARCHAR(128) NOT NULL COMMENT '记录行键',
    record_index INT NOT NULL DEFAULT 0 COMMENT '在任务内的序号',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除行',
    task_status VARCHAR(32) NOT NULL COMMENT '任务状态快照',
    file_key VARCHAR(128) NULL COMMENT '文件名快照',
    image_urls TEXT NULL COMMENT '图片列表快照',
    emp_no VARCHAR(64) NULL COMMENT '工号',
    emp_name VARCHAR(255) NULL COMMENT '姓名',
    base_name VARCHAR(255) NULL COMMENT '去序号后缀姓名',
    country VARCHAR(64) NULL COMMENT '国家',
    country_key VARCHAR(64) NULL COMMENT '国家检索键',
    warehouse VARCHAR(128) NULL COMMENT '仓库',
    warehouse_key VARCHAR(128) NULL COMMENT '仓库检索键',
    work_date VARCHAR(32) NULL COMMENT '日期',
    agency VARCHAR(255) NULL COMMENT '中介',
    agency_key VARCHAR(255) NULL COMMENT '中介检索键',
    shift VARCHAR(64) NULL COMMENT '班次',
    arrival VARCHAR(32) NULL COMMENT '到达',
    departure VARCHAR(32) NULL COMMENT '离开',
    pause_minutes VARCHAR(32) NULL COMMENT '休息分钟',
    signature VARCHAR(128) NULL COMMENT '签名',
    observations TEXT NULL COMMENT '备注',
    page_num VARCHAR(32) NULL COMMENT '页码',
    smart_mark VARCHAR(255) NULL COMMENT '智能标记',
    exception_type VARCHAR(64) NULL COMMENT '异常类型',
    task_created_at DATETIME NOT NULL COMMENT '任务创建时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_row (task_id, row_key),
    INDEX idx_user_status_created (user_id, task_status, task_created_at),
    INDEX idx_task_id (task_id),
    INDEX idx_dup (base_name, work_date, country_key, warehouse_key, agency_key),
    INDEX idx_emp_name (emp_name),
    INDEX idx_emp_no (emp_no),
    INDEX idx_billing_confirmed_date_agency (task_status, work_date, agency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务员工记录行';

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

-- AI 识别提示词（按国家，运行时唯一数据源；标准模板由应用启动/初始化脚本播种）
CREATE TABLE IF NOT EXISTS recognition_prompt (
    country_code VARCHAR(16) NOT NULL PRIMARY KEY COMMENT 'default 或 CN/FR/NL/IT 等',
    ai_prompt MEDIUMTEXT NOT NULL COMMENT '主要识别提示词',
    continue_prompt TEXT NOT NULL COMMENT '继续输出提示词',
    seed_version INT NOT NULL DEFAULT 1 COMMENT '内置模板版本号',
    user_modified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=用户曾在配置页保存过',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='识别提示词表';

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
    '$2a$10$Q.zLFZ7mN9u3OtU785ALoe10PZnM4xCboGypUWj4eifahiASCdF2S',
    'admin',
    '系统管理员',
    'active'
) ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    real_name = VALUES(real_name),
    status = VALUES(status);

-- 初始化默认配置
INSERT INTO plugin_config (config_key, config_value, config_type, description) VALUES
('feishu_bitable_app_token_DEFAULT', '', 'string', '默认多维表格APP Token'),
('feishu_bitable_table_id_DEFAULT', '', 'string', '默认多维表格Table ID'),
('feishu_field_mapping_DEFAULT', '[
    {"aiField":"NO","feishuField":"NO","type":"string","required":true},
    {"aiField":"Pays","feishuField":"Pays","type":"string","required":false},
    {"aiField":"Entrepot","feishuField":"Entrepôt","type":"string","required":false},
    {"aiField":"NOM_PRENOM","feishuField":"NOM PRENOM","type":"string","required":false},
    {"aiField":"AGENCE_INTERIMAIRE","feishuField":"AGENCE INTERIMAIRE","type":"string","required":false},
    {"aiField":"HORAIRES_DU_TRAVAIL","feishuField":"HORAIRES DU TRAVAIL","type":"string","required":false},
    {"aiField":"Date","feishuField":"Date","type":"date","required":true},
    {"aiField":"ARRIVEE","feishuField":"ARRIVE","type":"string","required":false},
    {"aiField":"DEPAR","feishuField":"DEPAR","type":"string","required":false},
    {"aiField":"PAUSE","feishuField":"PAUS","type":"number","required":true},
    {"aiField":"SIGNATURE","feishuField":"SIGNATURE","type":"string","required":false},
    {"aiField":"Observations","feishuField":"Observations","type":"string","required":false},
    {"aiField":"SmartMark","feishuField":"Mark","type":"string","required":false}
]', 'json', '默认字段映射配置'),
('recognition_batch_size', '100', 'number', '识别批次大小'),
('auto_confirm', 'false', 'boolean', '是否自动确认'),
('notification_enabled', 'true', 'boolean', '是否启用通知'),
('recognition_prompt_seed_version', '2', 'number', '内置识别提示词模板版本；应用启动时用于刷新标准提示词'),
('current_working_country', 'default', 'string', '全局工作国家（default 解析为法国 FR）'),
('recognition_engine', 'mimo', 'string', '识别模型引擎（mimo / deepseek，全局统一，不区分国家）')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);

-- 异步导出任务（与 migration/005_export_jobs.sql 一致）
CREATE TABLE IF NOT EXISTS export_jobs (
    id VARCHAR(64) PRIMARY KEY COMMENT '导出任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '发起用户',
    export_type VARCHAR(32) NOT NULL COMMENT 'task_list | employee_records',
    status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|running|completed|failed',
    query_json TEXT COMMENT '导出筛选条件 JSON',
    file_name VARCHAR(255) COMMENT '下载文件名',
    file_path VARCHAR(512) COMMENT '服务器文件路径',
    row_count BIGINT NOT NULL DEFAULT 0 COMMENT '导出行数',
    error_message VARCHAR(1024) COMMENT '失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    expires_at DATETIME NULL,
    dismissed_at DATETIME NULL COMMENT '用户从导出中心清空的时间',
    INDEX idx_export_user_created (user_id, created_at DESC),
    INDEX idx_export_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步导出任务';

-- 系统角色（010_system_roles）
CREATE TABLE IF NOT EXISTS system_role (
    role_key VARCHAR(32) PRIMARY KEY COMMENT '角色标识，如 admin、user、manager',
    role_name VARCHAR(64) NOT NULL COMMENT '角色显示名',
    built_in TINYINT(1) NOT NULL DEFAULT 0 COMMENT '内置角色不可删除',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色定义';

-- 用户-角色多对多（023_user_roles）
-- 主键为自增 id（单列）；(user_id, role_key) 以唯一约束保证 DB 层去重
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
    role_key VARCHAR(32) NOT NULL COMMENT '角色标识',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_key),
    INDEX idx_user_role_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联';

-- 角色数据权限（009_role_data_scope + 024 work_region）
CREATE TABLE IF NOT EXISTS role_data_scope (
    role VARCHAR(32) PRIMARY KEY COMMENT '角色标识',
    scope_type ENUM('all', 'restricted') NOT NULL DEFAULT 'restricted' COMMENT 'all=全部数据，restricted=按维度过滤',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色数据范围主配置';

CREATE TABLE IF NOT EXISTS role_data_dimension_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(32) NOT NULL COMMENT '角色标识',
    dimension ENUM('owner_user', 'country', 'warehouse', 'agency', 'work_region') NOT NULL COMMENT '业务维度',
    value VARCHAR(255) NOT NULL COMMENT '维度值；owner_user 可为用户ID或 __self__',
    UNIQUE KEY uk_role_dim_val (role, dimension, value),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色数据维度规则';

-- 飞书多维表按国家配置（014_feishu_country_config）
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

-- 识别公平队列（012_recognition_queue）
CREATE TABLE IF NOT EXISTS recognition_queue_jobs (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    config_country VARCHAR(64) NULL,
    client VARCHAR(64) NULL,
    job_source VARCHAR(32) NOT NULL COMMENT 'user|recovery|pages',
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    instance_id VARCHAR(96) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    INDEX idx_rq_status_created (status, created_at),
    INDEX idx_rq_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='识别队列任务';

-- 任务提醒（007 + 008–013 合并列）
CREATE TABLE IF NOT EXISTS reminder_rules (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    task_statuses JSON NOT NULL COMMENT '如 ["processed"]',
    scope_countries JSON NULL COMMENT '适用工作国家，空=全部',
    scope_roles JSON NULL COMMENT '适用任务创建者角色，空=全部',
    interval_value DECIMAL(10, 1) NOT NULL DEFAULT 1.0 COMMENT '间隔数值，最多一位小数',
    interval_unit VARCHAR(16) NOT NULL DEFAULT 'day' COMMENT 'minute|hour|day|week',
    schedule_hour_of_day TINYINT NULL COMMENT '0-23，仅 day/week 生效；NULL=不限制时刻',
    message_template TEXT NOT NULL,
    message_template_supervisor TEXT NULL COMMENT '非任务创建者提醒文案',
    message_template_locales TEXT NULL COMMENT '操作者多语言文案 JSON',
    message_template_supervisor_locales TEXT NULL COMMENT '督办人多语言文案 JSON',
    include_task_creator TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_run_at DATETIME NULL,
    last_hit_count INT NOT NULL DEFAULT 0,
    last_sent_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reminder_rules_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务提醒规则';

CREATE TABLE IF NOT EXISTS reminder_rule_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    rule_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_reminder_rule_user (rule_id, user_id),
    INDEX idx_reminder_rule_users_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则-提醒人';

CREATE TABLE IF NOT EXISTS reminder_deliveries (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    period_bucket VARCHAR(32) NOT NULL,
    channel_site TINYINT NOT NULL DEFAULT 0,
    channel_feishu TINYINT NOT NULL DEFAULT 0,
    feishu_status VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_delivery (rule_id, task_id, user_id, period_bucket),
    INDEX idx_reminder_delivery_rule (rule_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒投递去重';

CREATE TABLE IF NOT EXISTS user_notifications (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NULL,
    period_bucket VARCHAR(32) NULL,
    title VARCHAR(256) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(512) NULL,
    content_vars TEXT NULL COMMENT '提醒渲染变量 JSON',
    feishu_message_id VARCHAR(64) NULL COMMENT '飞书消息ID，用于同周期撤回重发',
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_notifications_user (user_id, created_at DESC),
    INDEX idx_user_notifications_unread (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内消息';

CREATE TABLE IF NOT EXISTS reminder_feishu_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    locale_key VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'locale，如 fr-FR',
    feishu_message_id VARCHAR(64) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_feishu_message (user_id, rule_id, locale_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒规则飞书消息状态（按 locale 分条）';

CREATE TABLE IF NOT EXISTS reminder_schedules (
    id VARCHAR(64) PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    period_index BIGINT NOT NULL,
    period_bucket VARCHAR(32) NOT NULL,
    due_at DATETIME NOT NULL,
    status_entered_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reminder_schedule (rule_id, task_id, user_id, period_bucket),
    INDEX idx_reminder_schedule_due (status, due_at),
    INDEX idx_reminder_schedule_task (task_id, status),
    INDEX idx_reminder_schedule_rule (rule_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒计划';

-- 员工主档（020_employees）
CREATE TABLE IF NOT EXISTS employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_no VARCHAR(16) NOT NULL COMMENT '系统工号 FR00001',
    region_code VARCHAR(8) NOT NULL COMMENT '工作地区 task.promptCountry',
    agency_key VARCHAR(255) NOT NULL COMMENT '中介机构规范化',
    match_name VARCHAR(255) NOT NULL COMMENT '发号比对姓名(默认含流水号)',
    display_name VARCHAR(255) NULL COMMENT '最近展示姓名',
    status TINYINT NOT NULL DEFAULT 1,
    first_created_at DATETIME NOT NULL,
    last_attendance_date DATE NULL,
    last_seen_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_no (emp_no),
    UNIQUE KEY uk_identity (region_code, agency_key, match_name),
    INDEX idx_region (region_code),
    INDEX idx_match_name (match_name),
    INDEX idx_last_attendance (last_attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工主档';

CREATE TABLE IF NOT EXISTS employee_serial_counters (
    region_code VARCHAR(8) PRIMARY KEY,
    next_seq INT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工工号序号';

-- 内置角色与管理员角色关联
INSERT IGNORE INTO system_role (role_key, role_name, built_in, sort_order) VALUES
('admin', '管理员', 1, 0),
('user', '普通用户', 1, 10);

INSERT IGNORE INTO user_role (user_id, role_key) VALUES ('admin001', 'admin');