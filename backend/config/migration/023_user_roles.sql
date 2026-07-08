-- 用户-角色多对多关联；users.role 保留为主角色（展示/兼容）
CREATE TABLE IF NOT EXISTS user_role (
    user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
    role_key VARCHAR(32) NOT NULL COMMENT '角色标识',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_key),
    INDEX idx_user_role_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联';

INSERT IGNORE INTO user_role (user_id, role_key)
SELECT id, role FROM users
WHERE status != 'deleted' AND role IS NOT NULL AND TRIM(role) != '';
