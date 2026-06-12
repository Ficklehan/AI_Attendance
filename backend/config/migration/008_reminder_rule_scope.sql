-- 提醒规则：按工作国家 / 创建者角色限定适用范围
ALTER TABLE reminder_rules
    ADD COLUMN scope_countries JSON NULL COMMENT '适用工作国家，空=全部，匹配 tasks.prompt_country' AFTER task_statuses,
    ADD COLUMN scope_roles JSON NULL COMMENT '适用任务创建者角色，空=全部' AFTER scope_countries;
