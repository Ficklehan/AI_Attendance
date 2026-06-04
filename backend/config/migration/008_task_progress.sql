-- 识别进度轻量字段，避免轮询拉取整段 raw_data
ALTER TABLE tasks ADD COLUMN progress_row_count INT NOT NULL DEFAULT 0 COMMENT '识别进行中已解析行数' AFTER ai_raw_output;
