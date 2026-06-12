-- 提醒周期：支持一位小数，并增加分钟单位
ALTER TABLE reminder_rules
    MODIFY COLUMN interval_value DECIMAL(10, 1) NOT NULL DEFAULT 1.0 COMMENT '间隔数值，最多一位小数',
    MODIFY COLUMN interval_unit VARCHAR(16) NOT NULL DEFAULT 'day' COMMENT 'minute|hour|day|week';
