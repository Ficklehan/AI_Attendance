-- 将 user_role 的复合主键 (user_id, role_key) 改造为自增 id 单列主键
-- 保留复合唯一约束 uk_user_role 以维持 DB 层去重（业务层按 user_id + role_key 去重）
--
-- 仅用于「已应用原始复合主键版 023」的存量库；全新库直接使用 init.sql（已是自增 id）。
-- 若 user_role 已是自增 id 主键，本脚本会报错，可安全跳过。
-- 应用启动时 UserRoleDatabaseBootstrap 亦会自愈此结构，无需手工执行。
ALTER TABLE user_role
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD UNIQUE KEY uk_user_role (user_id, role_key);
