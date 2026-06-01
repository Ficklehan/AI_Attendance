-- Run once on existing databases to ensure status enum supports task cancellation
ALTER TABLE tasks
    MODIFY COLUMN status ENUM('processing', 'processed', 'confirmed', 'failed', 'cancelled')
    NOT NULL DEFAULT 'processing' COMMENT '任务状态';
