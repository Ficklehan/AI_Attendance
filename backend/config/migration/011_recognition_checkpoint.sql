-- 识别断点续做：检查点 JSON + 心跳时间
ALTER TABLE tasks
    ADD COLUMN recognition_checkpoint TEXT NULL COMMENT '识别断点 JSON' AFTER progress_row_count,
    ADD COLUMN recognition_heartbeat_at DATETIME NULL COMMENT '识别任务心跳' AFTER recognition_checkpoint;
