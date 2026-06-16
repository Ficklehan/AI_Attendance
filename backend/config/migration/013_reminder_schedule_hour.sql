-- 按天/按周提醒：达到周期后在当天指定时刻发送
ALTER TABLE reminder_rules
    ADD COLUMN schedule_hour_of_day TINYINT NULL COMMENT '0-23，仅 day/week 生效；NULL=不限制时刻' AFTER interval_unit;
