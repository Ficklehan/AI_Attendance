-- 角色数据权限：按角色配置多业务维度数据范围
CREATE TABLE IF NOT EXISTS role_data_scope (
    role VARCHAR(32) PRIMARY KEY COMMENT '角色标识，如 admin、user',
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
