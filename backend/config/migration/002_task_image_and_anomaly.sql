-- Run once on existing databases after 001_task_sync_and_country.sql
ALTER TABLE tasks
    ADD COLUMN image_urls JSON NULL COMMENT '多图 URL 列表' AFTER confirmed_data,
    ADD COLUMN anomaly_summary JSON NULL COMMENT '异常摘要/识别跟踪' AFTER image_urls;
