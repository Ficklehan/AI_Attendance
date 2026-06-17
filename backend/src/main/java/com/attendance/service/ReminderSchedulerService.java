package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.FeishuProperties;
import com.attendance.dto.NotificationContentVars;
import com.attendance.dto.SiteNotificationReplaceResult;
import com.attendance.entity.ReminderDelivery;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.ReminderSchedule;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.mapper.ReminderDeliveryMapper;
import com.attendance.mapper.ReminderFeishuMessageMapper;
import com.attendance.mapper.ReminderRuleMapper;
import com.attendance.mapper.ReminderScheduleMapper;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仅执行 {@link ReminderSchedule} 中已到期的计划项；发送时刻由 {@link ReminderScheduleService} 按规则配置写入。
 */
@Service
public class ReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerService.class);
    private static final DateTimeFormatter CONTENT_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int DUE_BATCH_LIMIT = 200;

    /** 轻量轮询到期计划，不再全表扫描任务猜时间。 */
    private static final long EXECUTOR_INTERVAL_MS = 5_000;
    private static final long EXECUTOR_INITIAL_DELAY_MS = 15_000;

    @Autowired
    private PluginConfigService pluginConfigService;

    @Autowired
    private ReminderRuleMapper reminderRuleMapper;

    @Autowired
    private ReminderScheduleMapper reminderScheduleMapper;

    @Autowired
    private ReminderScheduleService reminderScheduleService;

    @Autowired
    private ReminderDeliveryMapper reminderDeliveryMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private FeishuService feishuService;

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private ReminderFeishuMessageMapper reminderFeishuMessageMapper;

    @Scheduled(fixedDelay = EXECUTOR_INTERVAL_MS, initialDelay = EXECUTOR_INITIAL_DELAY_MS)
    public void runReminders() {
        if (!pluginConfigService.isNotificationEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ReminderSchedule> dueSchedules = reminderScheduleMapper.selectDuePending(now, DUE_BATCH_LIMIT);
        if (dueSchedules.isEmpty()) {
            return;
        }
        Map<String, User> userCache = new HashMap<>();
        Map<String, ReminderRule> ruleCache = new HashMap<>();
        Map<String, Task> taskCache = new HashMap<>();

        // ruleId -> recipientId -> locale -> schedules in this batch
        Map<String, Map<String, Map<String, List<DueItem>>>> batches = new LinkedHashMap<>();

        for (ReminderSchedule schedule : dueSchedules) {
            ReminderRule rule = ruleCache.computeIfAbsent(
                    schedule.getRuleId(), reminderRuleMapper::selectById);
            if (rule == null || !rule.isEnabled()) {
                reminderScheduleMapper.markCancelled(schedule.getId());
                continue;
            }
            Task task = taskCache.computeIfAbsent(
                    schedule.getTaskId(), taskMapper::selectTaskByTaskId);
            if (task == null || !taskStillEligible(task, rule)) {
                reminderScheduleMapper.markCancelled(schedule.getId());
                continue;
            }
            User recipient = getUser(schedule.getUserId(), userCache);
            if (recipient == null || !"active".equals(recipient.getStatus())) {
                reminderScheduleMapper.markCancelled(schedule.getId());
                continue;
            }
            if (reminderDeliveryMapper.existsDelivery(
                    schedule.getRuleId(),
                    schedule.getTaskId(),
                    schedule.getUserId(),
                    schedule.getPeriodBucket()) > 0) {
                reminderScheduleMapper.markCancelled(schedule.getId());
                continue;
            }
            String locale = ReminderLocaleSupport.resolveLocale(
                    ReminderSupport.normalizeTaskCountry(task.getPromptCountry()));
            batches
                    .computeIfAbsent(schedule.getRuleId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(schedule.getUserId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(locale, k -> new ArrayList<>())
                    .add(new DueItem(schedule, rule, task, recipient));
        }

        for (Map.Entry<String, Map<String, Map<String, List<DueItem>>>> ruleEntry : batches.entrySet()) {
            String ruleId = ruleEntry.getKey();
            ReminderRule rule = ruleCache.get(ruleId);
            int hitCount = 0;
            int sentCount = 0;
            for (Map.Entry<String, Map<String, List<DueItem>>> recipientEntry : ruleEntry.getValue().entrySet()) {
                for (Map.Entry<String, List<DueItem>> localeEntry : recipientEntry.getValue().entrySet()) {
                    List<DueItem> items = localeEntry.getValue();
                    if (items.isEmpty()) {
                        continue;
                    }
                    int delivered = dispatchAggregated(rule, items, userCache);
                    if (delivered > 0) {
                        hitCount += items.size();
                        sentCount += delivered;
                    }
                }
            }
            reminderRuleMapper.updateLastRun(ruleId, hitCount, sentCount);
            if (sentCount > 0) {
                log.info("提醒计划执行完成 ruleId={} name={} hitTasks={} deliveries={}",
                        ruleId, rule != null ? rule.getName() : "-", hitCount, sentCount);
            }
        }
    }

    private int dispatchAggregated(ReminderRule rule, List<DueItem> items, Map<String, User> userCache) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        DueItem anchor = items.get(0);
        String recipientId = anchor.recipient.getId();
        String locale = ReminderLocaleSupport.resolveLocale(
                ReminderSupport.normalizeTaskCountry(anchor.task.getPromptCountry()));

        List<Task> userTasks = items.stream()
                .map(item -> item.task)
                .distinct()
                .sorted(Comparator.comparing(
                        t -> t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int delivered = 0;
        for (DueItem item : items) {
            recordDelivery(rule, item.task, recipientId, item.schedule.getPeriodBucket(), item.recipient);
            reminderScheduleMapper.markSent(item.schedule.getId());
            reminderScheduleService.scheduleFollowingPeriod(
                    rule,
                    item.task,
                    recipientId,
                    item.schedule.getPeriodIndex(),
                    item.schedule.getStatusEnteredAt());
            delivered++;
        }

        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        String statusLabel = statuses != null && statuses.size() == 1 ? statuses.get(0) : "processed";
        Task latest = userTasks.get(0);
        LocalDateTime latestTime = latest.getUpdatedAt() != null ? latest.getUpdatedAt() : latest.getCreatedAt();
        User creator = getUser(latest.getUserId(), userCache);
        boolean recipientIsOperator = userTasks.stream().allMatch(t -> recipientId.equals(t.getUserId()));
        List<String> creatorNameList = collectCreatorNames(userTasks, userCache);
        String creatorNames = ReminderMessageBuilder.joinCreatorNames(creatorNameList, locale);

        ReminderMessageBuilder.LocaleTemplates localeTemplates = ReminderMessageBuilder.resolveLocaleTemplates(rule);
        String template = ReminderMessageBuilder.pickTemplate(localeTemplates, rule, locale, recipientIsOperator);
        String body = ReminderMessageBuilder.renderBody(
                userTasks.size(),
                rule.getIntervalValue(),
                rule.getIntervalUnit(),
                statusLabel,
                latest.getTaskId(),
                latestTime,
                ReminderMessageBuilder.displayName(anchor.recipient),
                creator != null ? ReminderMessageBuilder.displayName(creator) : "",
                creatorNames,
                template,
                locale);
        String title = ReminderMessageBuilder.notificationTitle(rule, locale);
        String siteLink = ReminderSupport.buildPcTaskLink(feishuProperties.getFrontendLoginUrl(), latest.getTaskId());
        String feishuLink = ReminderSupport.buildMiniprogramTaskApplink(feishuProperties.getAppId(), latest.getTaskId());
        NotificationContentVars contentVars = buildContentVars(
                userTasks.size(),
                rule,
                statusLabel,
                latest,
                latestTime,
                ReminderMessageBuilder.displayName(anchor.recipient),
                creator != null ? ReminderMessageBuilder.displayName(creator) : "",
                creatorNameList,
                recipientIsOperator);
        String periodBucket = ReminderSupport.aggregatePeriodBucket(locale);
        SiteNotificationReplaceResult replaced = userNotificationService.replaceSiteNotification(
                recipientId, rule.getId(), periodBucket, title, body, siteLink, contentVars.toJson());
        String webLoginLink = ReminderSupport.buildFeishuWebLoginTaskLink(
                feishuProperties.getApiBaseUrl(), latest.getTaskId());
        String webApplink = ReminderSupport.buildFeishuWebApplink(webLoginLink);
        sendFeishuIfPossible(rule.getId(), locale, anchor.recipient, title, body,
                webLoginLink, webApplink, feishuLink, replaced);
        return delivered;
    }

    private boolean taskStillEligible(Task task, ReminderRule rule) {
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        if (statuses == null || !statuses.contains(task.getStatus())) {
            return false;
        }
        User creator = userMapper.selectUserById(task.getUserId());
        return ReminderSupport.taskMatchesRuleScope(
                task,
                creator,
                ReminderSupport.parseScopeList(rule.getScopeCountriesJson()),
                ReminderSupport.parseScopeList(rule.getScopeRolesJson()));
    }

    private void recordDelivery(ReminderRule rule, Task task, String recipientId, String periodBucket, User recipient) {
        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setId(IdGenerator.generateId());
        delivery.setRuleId(rule.getId());
        delivery.setTaskId(task.getTaskId());
        delivery.setUserId(recipientId);
        delivery.setPeriodBucket(periodBucket);
        delivery.setChannelSite(true);
        boolean feishuOk = recipient.getFeishuUserId() != null && !recipient.getFeishuUserId().trim().isEmpty();
        delivery.setChannelFeishu(feishuOk);
        delivery.setFeishuStatus(feishuOk ? "pending" : "skipped");
        reminderDeliveryMapper.insertDelivery(delivery);
    }

    private void sendFeishuIfPossible(String ruleId,
                                      String locale,
                                      User recipient,
                                      String title,
                                      String body,
                                      String webLoginLink,
                                      String webApplink,
                                      String miniprogramLink,
                                      SiteNotificationReplaceResult replaced) {
        String feishuUserId = recipient.getFeishuUserId();
        if (feishuUserId == null || feishuUserId.trim().isEmpty()) {
            log.debug("跳过飞书提醒：用户未绑定飞书 userId={}", recipient.getId());
            return;
        }
        String localeKey = locale != null && !locale.trim().isEmpty()
                ? locale.trim()
                : ReminderLocaleSupport.DEFAULT_LOCALE;
        String previousMessageId = reminderFeishuMessageMapper.selectMessageId(
                recipient.getId(), ruleId, localeKey);
        if (previousMessageId == null || previousMessageId.trim().isEmpty()) {
            previousMessageId = replaced != null ? replaced.getPreviousFeishuMessageId() : null;
        }
        if (previousMessageId != null && !previousMessageId.trim().isEmpty()) {
            try {
                feishuService.recallMessage(previousMessageId);
            } catch (Exception e) {
                log.warn("撤回上一条飞书提醒失败 userId={} ruleId={} messageId={}: {}",
                        recipient.getId(), ruleId, previousMessageId, e.getMessage());
            }
        }
        try {
            JSONObject card = buildFeishuCard(title, body, webLoginLink, webApplink, miniprogramLink, localeKey);
            String messageId = feishuService.sendCardMessage(feishuUserId, card);
            if (messageId != null && !messageId.trim().isEmpty()) {
                reminderFeishuMessageMapper.upsertMessageId(recipient.getId(), ruleId, localeKey, messageId);
                if (replaced != null) {
                    userNotificationService.updateFeishuMessageId(
                            replaced.getNotificationId(), recipient.getId(), messageId);
                }
                log.info("飞书提醒已发送 userId={} ruleId={} messageId={}",
                        recipient.getId(), ruleId, messageId);
            }
        } catch (Exception e) {
            log.warn("飞书提醒发送失败 userId={} feishuUserId={} ruleId={}: {}",
                    recipient.getId(), feishuUserId, ruleId, e.getMessage());
        }
    }

    private JSONObject buildFeishuCard(String title,
                                       String body,
                                       String webLoginLink,
                                       String webApplink,
                                       String miniprogramLink,
                                       String locale) {
        JSONObject card = new JSONObject();
        card.put("config", new JSONObject().fluentPut("wide_screen_mode", true));
        JSONObject header = new JSONObject();
        header.put("title", new JSONObject().fluentPut("tag", "plain_text").fluentPut("content", title));
        header.put("template", "blue");
        card.put("header", header);
        JSONObject text = new JSONObject();
        text.put("tag", "div");
        text.put("text", new JSONObject().fluentPut("tag", "lark_md").fluentPut("content", body));
        card.put("elements", JSON.parseArray("[{\"tag\":\"div\"}]"));
        card.getJSONArray("elements").set(0, text);
        String defaultLink = webApplink != null && !webApplink.isEmpty()
                ? webApplink
                : webLoginLink;
        boolean hasLink = (defaultLink != null && !defaultLink.isEmpty())
                || (miniprogramLink != null && !miniprogramLink.isEmpty());
        if (hasLink) {
            JSONObject action = new JSONObject();
            action.put("tag", "action");
            JSONObject button = new JSONObject();
            button.put("tag", "button");
            String viewLabel = ReminderLocaleSupport.viewTaskLabel(locale);
            button.put("text", new JSONObject().fluentPut("tag", "plain_text").fluentPut("content", viewLabel));
            button.put("type", "primary");
            JSONObject multiUrl = new JSONObject();
            if (defaultLink != null && !defaultLink.isEmpty()) {
                multiUrl.put("url", defaultLink);
            }
            if (webLoginLink != null && !webLoginLink.isEmpty()) {
                multiUrl.put("pc_url", webLoginLink);
            }
            if (miniprogramLink != null && !miniprogramLink.isEmpty()) {
                multiUrl.put("android_url", miniprogramLink);
                multiUrl.put("ios_url", miniprogramLink);
            }
            if (!multiUrl.isEmpty()) {
                button.put("multi_url", multiUrl);
            } else if (defaultLink != null && !defaultLink.isEmpty()) {
                button.put("url", defaultLink);
            }
            action.put("actions", JSON.parseArray("[{}]"));
            action.getJSONArray("actions").set(0, button);
            card.getJSONArray("elements").add(action);
        }
        return card;
    }

    private User getUser(String userId, Map<String, User> cache) {
        if (userId == null) {
            return null;
        }
        return cache.computeIfAbsent(userId, id -> userMapper.selectUserById(id));
    }

    private List<String> collectCreatorNames(List<Task> tasks, Map<String, User> userCache) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Task task : tasks) {
            User creator = getUser(task.getUserId(), userCache);
            if (creator != null) {
                names.add(ReminderMessageBuilder.displayName(creator));
            }
        }
        return new ArrayList<>(names);
    }

    private static NotificationContentVars buildContentVars(int pendingCount,
                                                            ReminderRule rule,
                                                            String taskStatus,
                                                            Task latest,
                                                            LocalDateTime latestTime,
                                                            String recipientName,
                                                            String taskCreatorName,
                                                            List<String> creatorNames,
                                                            boolean recipientIsOperator) {
        NotificationContentVars contentVars = new NotificationContentVars();
        contentVars.setPendingCount(pendingCount);
        contentVars.setIntervalValue(rule.getIntervalValue() != null
                ? rule.getIntervalValue().toPlainString()
                : "1");
        contentVars.setIntervalUnit(rule.getIntervalUnit());
        contentVars.setTaskStatus(taskStatus);
        contentVars.setLatestTaskId(latest.getTaskId());
        contentVars.setLatestTaskTime(latestTime != null ? latestTime.format(CONTENT_TIME) : null);
        contentVars.setRecipientName(recipientName);
        contentVars.setTaskCreatorName(taskCreatorName);
        contentVars.setCreatorNames(creatorNames != null ? creatorNames : new ArrayList<>());
        contentVars.setRecipientIsOperator(recipientIsOperator);
        return contentVars;
    }

    private static final class DueItem {
        private final ReminderSchedule schedule;
        private final ReminderRule rule;
        private final Task task;
        private final User recipient;

        private DueItem(ReminderSchedule schedule, ReminderRule rule, Task task, User recipient) {
            this.schedule = schedule;
            this.rule = rule;
            this.task = task;
            this.recipient = recipient;
        }
    }
}
