package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 确保 task_recognition_event 表存在（migration 027）。
 */
@Component
@Order(18)
public class RecognitionEventDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecognitionEventDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public RecognitionEventDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS task_recognition_event ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '代理主键',"
                    + "task_id VARCHAR(64) NOT NULL COMMENT '任务ID',"
                    + "seq INT NOT NULL COMMENT '任务内单调序号，从 1 起',"
                    + "event_type VARCHAR(32) NOT NULL COMMENT 'record|page_done|status|error|complete',"
                    + "payload JSON NULL COMMENT '事件载荷',"
                    + "image_index INT NULL COMMENT '图片下标，从 0 起',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_task_seq (task_id, seq),"
                    + "INDEX idx_task_created (task_id, created_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                    + " COMMENT='任务识别事件日志'");
            log.info("task_recognition_event 表已就绪");
        } catch (Exception e) {
            log.error("创建 task_recognition_event 表失败", e);
        }
    }
}
