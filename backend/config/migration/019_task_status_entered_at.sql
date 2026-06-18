-- 任务进入当前状态的时间（提醒周期锚点，不随识别/校准更新）
ALTER TABLE tasks
    ADD COLUMN status_entered_at DATETIME NULL COMMENT '进入当前 status 的时间' AFTER status;

UPDATE tasks SET status_entered_at = updated_at WHERE status_entered_at IS NULL;
