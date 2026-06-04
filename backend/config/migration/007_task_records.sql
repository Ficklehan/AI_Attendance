-- 员工记录行级表（B+ 性能优化）：列表/导出/重名检测走 DB 分页，不再全量展开 tasks JSON

CREATE TABLE IF NOT EXISTS task_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '行ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '任务所属用户',
    row_key VARCHAR(128) NOT NULL COMMENT '记录行键(_rowKey或生成)',
    record_index INT NOT NULL DEFAULT 0 COMMENT '在任务内的序号',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除行',
    task_status VARCHAR(32) NOT NULL COMMENT '任务状态快照',
    file_key VARCHAR(128) NULL COMMENT '文件名快照',
    image_urls TEXT NULL COMMENT '图片列表快照',
    emp_no VARCHAR(64) NULL COMMENT '工号',
    emp_name VARCHAR(255) NULL COMMENT '姓名',
    base_name VARCHAR(255) NULL COMMENT '去序号后缀姓名(重名检测)',
    country VARCHAR(64) NULL COMMENT '国家',
    country_key VARCHAR(64) NULL COMMENT '国家检索键',
    warehouse VARCHAR(128) NULL COMMENT '仓库',
    warehouse_key VARCHAR(128) NULL COMMENT '仓库检索键',
    work_date VARCHAR(32) NULL COMMENT '日期',
    agency VARCHAR(255) NULL COMMENT '中介',
    agency_key VARCHAR(255) NULL COMMENT '中介检索键',
    shift VARCHAR(64) NULL COMMENT '班次',
    arrival VARCHAR(32) NULL COMMENT '到达',
    departure VARCHAR(32) NULL COMMENT '离开',
    pause_minutes VARCHAR(32) NULL COMMENT '休息分钟',
    signature VARCHAR(128) NULL COMMENT '签名',
    observations TEXT NULL COMMENT '备注',
    page_num VARCHAR(32) NULL COMMENT '页码',
    task_created_at DATETIME NOT NULL COMMENT '任务创建时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_row (task_id, row_key),
    INDEX idx_user_status_created (user_id, task_status, task_created_at),
    INDEX idx_task_id (task_id),
    INDEX idx_dup (base_name, work_date, country_key, warehouse_key, agency_key),
    INDEX idx_emp_name (emp_name),
    INDEX idx_emp_no (emp_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务员工记录行';

-- tasks 列表/筛选组合索引
ALTER TABLE tasks ADD INDEX idx_user_status_created (user_id, status, created_at);
