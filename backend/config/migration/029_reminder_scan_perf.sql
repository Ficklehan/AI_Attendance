-- 提醒扫描剪枝：按状态 + 进入状态时间过滤久留任务
-- 存量 minute 规则对齐产品单位 hour/day/week

ALTER TABLE tasks
    ADD INDEX idx_tasks_status_entered (status, status_entered_at);

UPDATE reminder_rules
SET interval_unit = 'hour'
WHERE LOWER(interval_unit) = 'minute';
