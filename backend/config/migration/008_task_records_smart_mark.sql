-- task_records 增加识别标记列，供列表筛选与展示

ALTER TABLE task_records
    ADD COLUMN smart_mark VARCHAR(255) NULL COMMENT '识别标记(SmartMark)' AFTER page_num;
