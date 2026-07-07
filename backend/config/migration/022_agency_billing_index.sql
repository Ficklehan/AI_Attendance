-- 中介账单：按已确认记录 + 工作日期 + 中介查询
ALTER TABLE task_records
    ADD INDEX idx_billing_confirmed_date_agency (task_status, work_date, agency_key);
