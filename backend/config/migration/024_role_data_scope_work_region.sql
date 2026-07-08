-- 角色数据维度：补充 work_region（员工工作地区），与 country 并存供数据权限合并
ALTER TABLE role_data_dimension_rule
    MODIFY COLUMN dimension ENUM('owner_user', 'country', 'warehouse', 'agency', 'work_region') NOT NULL
    COMMENT '业务维度';
