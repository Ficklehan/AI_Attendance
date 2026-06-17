-- 用户工作地区（管理员可在用户管理中配置；空则回退系统默认）
ALTER TABLE users
    ADD COLUMN working_country VARCHAR(64) NULL COMMENT '工作地区代码，如 CN、FR；default 或空表示系统默认' AFTER employee_id;
