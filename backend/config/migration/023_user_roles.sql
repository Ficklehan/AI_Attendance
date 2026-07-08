-- 用户-角色多对多关联；users.role 保留为主角色（展示/兼容）
-- 主键为自增 id（单列）；(user_id, role_key) 以唯一约束保证 DB 层去重
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
    role_key VARCHAR(32) NOT NULL COMMENT '角色标识',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_key),
    INDEX idx_user_role_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联';

INSERT IGNORE INTO user_role (user_id, role_key)
SELECT id, role FROM users
WHERE status != 'deleted' AND role IS NOT NULL AND TRIM(role) != '';
