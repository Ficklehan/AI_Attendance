-- task_records 增加异常类型列（与确认页 ExceptionType 对齐）

ALTER TABLE task_records
    ADD COLUMN exception_type VARCHAR(64) NULL COMMENT '异常类型(ExceptionType)' AFTER smart_mark;
