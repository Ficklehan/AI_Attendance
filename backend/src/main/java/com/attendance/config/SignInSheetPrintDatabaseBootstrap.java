package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 确保签到表打印归档表存在（migration 028）。
 */
@Component
@Order(19)
public class SignInSheetPrintDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SignInSheetPrintDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public SignInSheetPrintDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sign_in_sheet_print_job ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '代理主键',"
                    + "country_code VARCHAR(32) NOT NULL COMMENT '国家/工作地区代码',"
                    + "work_date DATE NOT NULL COMMENT '纸面工作日期',"
                    + "warehouse VARCHAR(128) NOT NULL COMMENT '仓库',"
                    + "sheet_locale VARCHAR(16) NOT NULL COMMENT '纸面语言',"
                    + "page_count INT NOT NULL COMMENT '打印页数',"
                    + "printed_by VARCHAR(64) NOT NULL COMMENT '打印人用户ID',"
                    + "printed_at DATETIME NOT NULL COMMENT '打印时间',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_print_job_country_date (country_code, work_date),"
                    + "KEY idx_print_job_printed_by (printed_by)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                    + " COMMENT='签到表打印批次'");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sign_in_sheet_print_row ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '代理主键',"
                    + "job_id BIGINT NOT NULL COMMENT '打印批次ID',"
                    + "row_index INT NOT NULL COMMENT '批次内行序，从 0 起',"
                    + "seq_no INT NULL COMMENT '纸面序号',"
                    + "person_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '姓名',"
                    + "agency_name VARCHAR(256) NOT NULL DEFAULT '' COMMENT '供应商名称',"
                    + "shift_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '班次',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_print_row_job (job_id),"
                    + "CONSTRAINT fk_print_row_job FOREIGN KEY (job_id)"
                    + " REFERENCES sign_in_sheet_print_job(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                    + " COMMENT='签到表打印人员行'");
            log.info("sign_in_sheet_print_job / sign_in_sheet_print_row 表已就绪");
        } catch (Exception e) {
            log.error("创建签到表打印归档表失败", e);
        }
    }
}
