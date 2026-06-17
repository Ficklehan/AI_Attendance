package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(15)
public class UserDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserDatabaseBootstrap.class);

    private final JdbcTemplate jdbcTemplate;

    public UserDatabaseBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureWorkingCountryColumn();
    }

    private void ensureWorkingCountryColumn() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'users' "
                            + "AND column_name = 'working_country'",
                    Integer.class);
            if (cnt != null && cnt > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN working_country VARCHAR(64) NULL "
                    + "COMMENT '工作地区代码，如 CN、FR；default 或空表示系统默认' AFTER employee_id");
            log.info("users.working_country 列已添加");
        } catch (Exception e) {
            log.warn("users.working_country 列初始化跳过: {}", e.getMessage());
        }
    }
}
