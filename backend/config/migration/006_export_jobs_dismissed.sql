-- 导出任务：支持用户一键清空（软隐藏已完成/失败记录，历史仍可查）
ALTER TABLE export_jobs
    ADD COLUMN dismissed_at DATETIME NULL COMMENT '用户从导出中心清空的时间' AFTER expires_at;
