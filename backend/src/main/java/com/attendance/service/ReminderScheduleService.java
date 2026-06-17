package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.ReminderSchedule;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.mapper.ReminderDeliveryMapper;
import com.attendance.mapper.ReminderRuleMapper;
import com.attendance.mapper.ReminderScheduleMapper;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 根据提醒规则配置计算 {@code due_at} 并写入计划表；调度器仅执行到期项。
 */
@Service
public class ReminderScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduleService.class);

    @Autowired
    private ReminderRuleMapper reminderRuleMapper;

    @Autowired
    private ReminderScheduleMapper reminderScheduleMapper;

    @Autowired
    private ReminderDeliveryMapper reminderDeliveryMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PluginConfigService pluginConfigService;

    /** 启动或规则变更后，为所有启用规则重建计划。 */
    @Transactional
    public void reconcileAllEnabledRules() {
        if (!pluginConfigService.isNotificationEnabled()) {
            return;
        }
        List<ReminderRule> rules = reminderRuleMapper.selectEnabled();
        for (ReminderRule rule : rules) {
            reconcileRule(rule);
        }
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
        reconcileRule(rule);
    }

    @Transactional
    public void onRuleDisabledOrDeleted(String ruleId) {
        if (ruleId != null) {
            reminderScheduleMapper.deleteReschedulableByRule(ruleId);
        }
    }

    @Transactional
    public void onTaskAnchorChanged(String taskId) {
        if (!pluginConfigService.isNotificationEnabled()) {
            return;
        }
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            return;
        }
        reminderScheduleMapper.deleteReschedulableByTask(taskId);
        List<ReminderRule> rules = reminderRuleMapper.selectEnabled();
        for (ReminderRule rule : rules) {
            if (taskMatchesRule(task, rule)) {
                scheduleNextForTaskRule(task, rule);
            }
        }
    }

    @Transactional
    public void onTaskLeftReminderScope(String taskId) {
        reminderScheduleMapper.deleteReschedulableByTask(taskId);
    }

  /** 发送完成后排下一条周期。 */
    @Transactional
    public void scheduleFollowingPeriod(ReminderRule rule,
                                        Task task,
                                        String recipientId,
                                        long sentPeriodIndex,
                                        LocalDateTime statusEnteredAt) {
        if (rule == null || task == null || recipientId == null || statusEnteredAt == null) {
            return;
        }
        long nextPeriod = sentPeriodIndex + 1;
        LocalDateTime dueAt = ReminderSupport.computeDueAtForPeriod(
                statusEnteredAt,
                nextPeriod,
                rule.getIntervalValue(),
                rule.getIntervalUnit(),
                rule.getScheduleHourOfDay());
        if (dueAt == null) {
            return;
        }
        upsertPendingSchedule(rule, task, recipientId, nextPeriod, statusEnteredAt, dueAt);
    }

    private void reconcileRule(ReminderRule rule) {
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        if (statuses == null || statuses.isEmpty()) {
            reminderScheduleMapper.deleteReschedulableByRule(rule.getId());
            return;
        }
        List<String> recipients = reminderRuleMapper.selectRecipientUserIds(rule.getId());
        if (recipients == null || recipients.isEmpty()) {
            reminderScheduleMapper.deleteReschedulableByRule(rule.getId());
            return;
        }
        reminderScheduleMapper.deleteReschedulableByRule(rule.getId());
        List<Task> tasks = taskMapper.selectTasksByStatuses(statuses);
        for (Task task : tasks) {
            if (taskMatchesRule(task, rule)) {
                scheduleNextForTaskRule(task, rule);
            }
        }
        log.debug("提醒规则计划已重建 ruleId={} taskCount={}", rule.getId(), tasks.size());
    }

    private void scheduleNextForTaskRule(Task task, ReminderRule rule) {
        Set<String> recipients = resolveRecipients(rule, task);
        LocalDateTime enteredAt = ReminderSupport.resolveStatusEnteredAt(task);
        if (enteredAt == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (String recipientId : recipients) {
            User recipient = userMapper.selectUserById(recipientId);
            if (recipient == null || !"active".equals(recipient.getStatus())) {
                continue;
            }
            long nextPeriod = ReminderSupport.resolveNextPeriodToSchedule(
                    enteredAt,
                    now,
                    rule.getIntervalValue(),
                    rule.getIntervalUnit(),
                    period -> reminderDeliveryMapper.existsDelivery(
                                    rule.getId(), task.getTaskId(), recipientId, String.valueOf(period)) > 0
                            || isSchedulePeriodSent(rule.getId(), task.getTaskId(), recipientId, period));
            LocalDateTime dueAt = ReminderSupport.computeDueAtForPeriod(
                    enteredAt,
                    nextPeriod,
                    rule.getIntervalValue(),
                    rule.getIntervalUnit(),
                    rule.getScheduleHourOfDay());
            if (dueAt == null) {
                continue;
            }
            upsertPendingSchedule(rule, task, recipientId, nextPeriod, enteredAt, dueAt);
        }
    }

    private void upsertPendingSchedule(ReminderRule rule,
                                       Task task,
                                       String recipientId,
                                       long periodIndex,
                                       LocalDateTime statusEnteredAt,
                                       LocalDateTime dueAt) {
        String periodBucket = String.valueOf(periodIndex);
        if (reminderDeliveryMapper.existsDelivery(
                rule.getId(), task.getTaskId(), recipientId, periodBucket) > 0) {
            return;
        }
        ReminderSchedule existing = reminderScheduleMapper.selectForRecipientPeriod(
                rule.getId(), task.getTaskId(), recipientId, periodBucket);
        if (existing != null) {
            if (ReminderSchedule.STATUS_SENT.equals(existing.getStatus())) {
                return;
            }
            existing.setPeriodIndex(periodIndex);
            existing.setDueAt(dueAt);
            existing.setStatusEnteredAt(statusEnteredAt);
            reminderScheduleMapper.updateReschedule(existing);
            log.debug("提醒计划已更新 ruleId={} taskId={} userId={} period={} dueAt={}",
                    rule.getId(), task.getTaskId(), recipientId, periodIndex, dueAt);
            return;
        }
        ReminderSchedule schedule = new ReminderSchedule();
        schedule.setId(IdGenerator.generateId());
        schedule.setRuleId(rule.getId());
        schedule.setTaskId(task.getTaskId());
        schedule.setUserId(recipientId);
        schedule.setPeriodIndex(periodIndex);
        schedule.setPeriodBucket(periodBucket);
        schedule.setDueAt(dueAt);
        schedule.setStatusEnteredAt(statusEnteredAt);
        schedule.setStatus(ReminderSchedule.STATUS_PENDING);
        reminderScheduleMapper.insertSchedule(schedule);
        log.debug("提醒计划已创建 ruleId={} taskId={} userId={} period={} dueAt={}",
                rule.getId(), task.getTaskId(), recipientId, periodIndex, dueAt);
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
        return ReminderSupport.taskMatchesRuleScope(
                task,
                creator,
                ReminderSupport.parseScopeList(rule.getScopeCountriesJson()),
                ReminderSupport.parseScopeList(rule.getScopeRolesJson()));
    }

    private boolean isSchedulePeriodSent(String ruleId, String taskId, String recipientId, long period) {
        ReminderSchedule existing = reminderScheduleMapper.selectForRecipientPeriod(
                ruleId, taskId, recipientId, String.valueOf(period));
        return existing != null && ReminderSchedule.STATUS_SENT.equals(existing.getStatus());
    }

    private Set<String> resolveRecipients(ReminderRule rule, Task task) {
        Set<String> recipients = new HashSet<>(reminderRuleMapper.selectRecipientUserIds(rule.getId()));
        if (rule.isIncludeTaskCreator() && task.getUserId() != null) {
            recipients.add(task.getUserId());
        }
        return recipients;
    }
}
