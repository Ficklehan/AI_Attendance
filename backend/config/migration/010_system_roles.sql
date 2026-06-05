-- 可配置系统角色（数据权限、功能权限、用户分配共用）
CREATE TABLE IF NOT EXISTS system_role (
    role_key VARCHAR(32) PRIMARY KEY COMMENT '角色标识，如 admin、user、manager',
    role_name VARCHAR(64) NOT NULL COMMENT '角色显示名',
    built_in TINYINT(1) NOT NULL DEFAULT 0 COMMENT '内置角色不可删除',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色定义';

INSERT IGNORE INTO system_role (role_key, role_name, built_in, sort_order) VALUES
('admin', '管理员', 1, 0),
('user', '普通用户', 1, 10);

-- users.role 从 ENUM 扩展为 VARCHAR，以支持自定义角色
ALTER TABLE users MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT '角色标识';
