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
            backfillSmartMark();
            backfillLegacySignature();
        } catch (Exception e) {
            log.warn("task_records 历史回填跳过: {}", e.getMessage());
        }
    }

    /** 补齐 smart_mark：对已有行但标记为空的任务重新同步（每轮限量，避免启动过慢） */
    private void backfillSmartMark() {
        List<String> taskIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT tr.task_id FROM task_records tr "
                        + "WHERE tr.smart_mark IS NULL OR TRIM(tr.smart_mark) = '' "
                        + "ORDER BY tr.task_id DESC LIMIT ?",
                String.class, BATCH);
        if (taskIds.isEmpty()) {
            return;
        }
        int synced = 0;
        for (String taskId : taskIds) {
            try {
                taskRecordSyncService.syncFromTaskId(taskId);
                synced++;
            } catch (Exception e) {
                log.debug("smart_mark 回填跳过 taskId={}: {}", taskId, e.getMessage());
            }
        }
        if (synced > 0) {
            log.info("task_records smart_mark 回填: {} 个任务", synced);
        }
    }

    /** 将员工签字列中的旧手写原文规范为三档标记（每轮限量） */
    private void backfillLegacySignature() {
        List<String> taskIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT tr.task_id FROM task_records tr "
                        + "WHERE tr.signature IS NOT NULL AND TRIM(tr.signature) != '' "
                        + "AND TRIM(tr.signature) NOT IN ('已签字确认', '未签字确认', '已签字') "
                        + "ORDER BY tr.task_id DESC LIMIT ?",
                String.class, BATCH);
        if (taskIds.isEmpty()) {
            return;
        }
        int synced = 0;
        for (String taskId : taskIds) {
            try {
                taskRecordSyncService.syncFromTaskId(taskId);
                synced++;
            } catch (Exception e) {
                log.debug("legacy signature 回填跳过 taskId={}: {}", taskId, e.getMessage());
            }
        }
        if (synced > 0) {
            log.info("task_records 员工签字列历史回填: {} 个任务", synced);
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
