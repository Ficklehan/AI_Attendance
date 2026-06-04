package com.attendance.config;

import com.attendance.mapper.TaskRecordMapper;
import com.attendance.service.TaskRecordSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时补齐历史任务的 task_records 行（仅处理尚未同步的任务）。
 */
@Component
@Order(17)
public class TaskRecordBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRecordBackfillRunner.class);
    private static final int BATCH = 50;

    private final JdbcTemplate jdbcTemplate;
    private final TaskRecordSyncService taskRecordSyncService;
    private final TaskRecordMapper taskRecordMapper;

    public TaskRecordBackfillRunner(JdbcTemplate jdbcTemplate,
                                    TaskRecordSyncService taskRecordSyncService,
                                    TaskRecordMapper taskRecordMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRecordSyncService = taskRecordSyncService;
        this.taskRecordMapper = taskRecordMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfillMissing();
        } catch (Exception e) {
            log.warn("task_records 历史回填跳过: {}", e.getMessage());
        }
    }

    private void backfillMissing() {
        Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'task_records'",
                Integer.class);
        if (tableExists == null || tableExists == 0) {
            return;
        }

        int offset = 0;
        int synced = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT t.task_id FROM tasks t "
                            + "LEFT JOIN task_records tr ON t.task_id = tr.task_id "
                            + "WHERE tr.task_id IS NULL "
                            + "AND (t.raw_data IS NOT NULL AND t.raw_data != '' "
                            + "OR t.confirmed_data IS NOT NULL AND t.confirmed_data != '') "
                            + "ORDER BY t.created_at DESC "
                            + "LIMIT ? OFFSET ?",
                    BATCH, offset);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                String taskId = String.valueOf(row.get("task_id"));
                if (taskRecordMapper.countByTaskId(taskId) > 0) {
                    continue;
                }
                taskRecordSyncService.syncFromTaskId(taskId);
                synced++;
            }
            offset += rows.size();
            if (rows.size() < BATCH) {
                break;
            }
        }
        if (synced > 0) {
            log.info("task_records 历史回填完成: {} 个任务", synced);
        }
    }
}
