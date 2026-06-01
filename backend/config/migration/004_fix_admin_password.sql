-- 修复默认管理员 admin 的密码哈希（明文密码: admin123）
-- 旧 init.sql 中的哈希与 admin123 不匹配，会导致「密码错误」
UPDATE users
SET password_hash = '$2a$10$Q.zLFZ7mN9u3OtU785ALoe10PZnM4xCboGypUWj4eifahiASCdF2S'
WHERE username = 'admin';
