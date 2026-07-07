-- 考勤记录仅保留已确认任务；清理失败/处理中等非确认任务的历史行
DELETE tr FROM task_records tr
INNER JOIN tasks t ON tr.task_id = t.task_id
WHERE t.status != 'confirmed';
