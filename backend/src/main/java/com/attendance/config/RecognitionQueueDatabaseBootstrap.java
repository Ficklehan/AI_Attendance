package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(17)
public class RecognitionQueueDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecognitionQueueDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public RecognitionQueueDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS recognition_queue_jobs ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "task_id VARCHAR(64) NOT NULL,"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "config_country VARCHAR(64) NULL,"
                    + "client VARCHAR(64) NULL,"
                    + "job_source VARCHAR(32) NOT NULL,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'pending',"
                    + "instance_id VARCHAR(96) NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "started_at DATETIME NULL,"
                    + "completed_at DATETIME NULL,"
                    + "INDEX idx_rq_status_created (status, created_at),"
                    + "INDEX idx_rq_task (task_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.debug("recognition_queue_jobs 表可能已存在: {}", e.getMessage());
        }
    }
}
