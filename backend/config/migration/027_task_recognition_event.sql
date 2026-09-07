-- 识别事件日志（按任务单调 seq，客户端 afterSeq 追赶）
CREATE TABLE IF NOT EXISTS task_recognition_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '代理主键',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    seq INT NOT NULL COMMENT '任务内单调序号，从 1 起',
    event_type VARCHAR(32) NOT NULL COMMENT 'record|page_done|status|error|complete',
    payload JSON NULL COMMENT '事件载荷',
    image_index INT NULL COMMENT '图片下标，从 0 起',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_seq (task_id, seq),
    INDEX idx_task_created (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务识别事件日志';
