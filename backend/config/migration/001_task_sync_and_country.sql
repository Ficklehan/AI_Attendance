-- Run once on existing databases before deploying this version
ALTER TABLE tasks
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'none'
        COMMENT '飞书同步: none|pending|synced|sync_failed' AFTER status,
    ADD COLUMN sync_error VARCHAR(512) NULL COMMENT '最近一次飞书同步错误' AFTER sync_status,
    ADD COLUMN prompt_country VARCHAR(64) NULL COMMENT '识别时快照的国家配置' AFTER sync_error;

ALTER TABLE tasks ADD INDEX idx_tasks_sync_status (sync_status);
