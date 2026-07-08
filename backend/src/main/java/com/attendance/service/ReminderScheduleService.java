package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.mapper.ReminderRuleMapper;
import com.attendance.mapper.ReminderScheduleMapper;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;

/**
 * 规则/任务变更时清理旧版计划表，并触发基于规则扫描的补发。
 */
@Service
public class ReminderScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduleService.class);

    @Autowired
    private ReminderRuleMapper reminderRuleMapper;

    @Autowired
    private ReminderScheduleMapper reminderScheduleMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PluginConfigService pluginConfigService;

    @Autowired
    @Lazy
    private ReminderSchedulerService reminderSchedulerService;

    @Transactional
    public void reconcileAllEnabledRules() {
        if (!pluginConfigService.isNotificationEnabled()) {
            cancelAllPendingSchedules();
            return;
        }
        reminderScheduleMapper.cancelAllPending();
        scheduleCatchUpAfterCommit("reconcile-all");
        log.info("提醒规则全量对齐完成，已排队历史欠账补发扫描");
    }

    @Transactional
    public void reconcileRule(String ruleId) {
        ReminderRule rule = reminderRuleMapper.selectById(ruleId);
        if (rule == null || !rule.isEnabled()) {
            if (ruleId != null) {
                reminderScheduleMapper.deleteReschedulableByRule(ruleId);
            }
            return;
        }
        if (!pluginConfigService.isNotificationEnabled()) {
            return;
        }
        reminderScheduleMapper.deleteReschedulableByRule(ruleId);
        scheduleCatchUpAfterCommit("reconcile-rule:" + ruleId);
        log.debug("提醒规则已对齐并排队补发扫描 ruleId={}", ruleId);
    }

    @Transactional
    public void onRuleDisabledOrDeleted(String ruleId) {
        if (ruleId != null) {
            reminderScheduleMapper.deleteReschedulableByRule(ruleId);
        }
    }

    @Transactional
    public void onTaskLeftReminderScope(String taskId) {
        reminderScheduleMapper.deleteReschedulableByTask(taskId);
    }

    @Transactional
    public void cancelAllPendingSchedules() {
        reminderScheduleMapper.cancelAllPending();
    }

    @Transactional
    public void onNotificationDisabled() {
        cancelAllPendingSchedules();
    }

    @Transactional
    public void onNotificationEnabled() {
        reminderScheduleMapper.cancelAllPending();
        scheduleCatchUpAfterCommit("notification-enabled");
    }

    /** 补发扫描在事务提交后异步执行，避免启用/保存规则 API 被扫描失败拖垮。 */
    private void scheduleCatchUpAfterCommit(String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runCatchUpSafely(reason);
                }
            });
            return;
        }
        runCatchUpSafely(reason);
    }

    private void runCatchUpSafely(String reason) {
        try {
            reminderSchedulerService.runCatchUpScan();
        } catch (Exception e) {
            log.warn("提醒补发扫描失败 reason={}: {}", reason, e.getMessage(), e);
        }
    }

    /** 任务状态离开提醒范围时取消旧计划（扫描层以 status_entered_at + 状态过滤为准）。 */
    @Transactional
    public void onTaskStatusChanged(String taskId) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            reminderScheduleMapper.deleteReschedulableByTask(taskId);
            return;
        }
        List<ReminderRule> rules = reminderRuleMapper.selectEnabled();
        boolean inScope = false;
        for (ReminderRule rule : rules) {
            if (taskMatchesRule(task, rule)) {
                inScope = true;
                break;
            }
        }
        if (!inScope) {
            reminderScheduleMapper.deleteReschedulableByTask(taskId);
        }
    }

    private boolean taskMatchesRule(Task task, ReminderRule rule) {
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        if (statuses == null || statuses.isEmpty() || task.getStatus() == null) {
            return false;
        }
        if (!statuses.contains(task.getStatus())) {
            return false;
        }
        User creator = userMapper.selectUserById(task.getUserId());
        List<String> creatorRoles = creator != null
                ? userRoleService.getRoleKeysForUserId(creator.getId()) : Collections.emptyList();
        return ReminderSupport.taskMatchesRuleScope(
                task,
                creator,
                creatorRoles,
                ReminderSupport.parseScopeList(rule.getScopeCountriesJson()),
                ReminderSupport.parseScopeList(rule.getScopeRolesJson()));
    }
}
