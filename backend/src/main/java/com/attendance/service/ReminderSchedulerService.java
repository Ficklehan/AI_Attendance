package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.FeishuProperties;
import com.attendance.dto.NotificationContentVars;
import com.attendance.dto.SiteNotificationReplaceResult;
import com.attendance.entity.ReminderDelivery;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.mapper.ReminderDeliveryMapper;
import com.attendance.mapper.ReminderFeishuMessageMapper;
import com.attendance.mapper.ReminderRuleMapper;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按提醒规则扫描到期任务，同一用户同一时刻跨规则合并为一条站内/飞书提醒。
 * 历史欠账周期在单次调度中一次性补齐投递记录，但只发送一条汇总消息。
 */
@Service
public class ReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerService.class);
    private static final DateTimeFormatter CONTENT_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 与设置页文案一致：每 15 分钟扫描 */
    private static final long SCAN_INTERVAL_MS = 900_000L;
    private static final long SCAN_INITIAL_DELAY_MS = 120_000L;
    /**
     * 逐周期回补窗口上限。窗口外欠账用 legacy_before_{scanFrom} 桶一次性结算，
     * 仍触发提醒，避免静默丢欠账或数万次逐 period 写入。
     */
    private static final long MAX_CATCHUP_PERIODS = 500L;
    private static final String LEGACY_BUCKET_PREFIX = "legacy_before_";

    @Autowired
    private PluginConfigService pluginConfigService;

    @Autowired
    private ReminderRuleMapper reminderRuleMapper;

    @Autowired
    private ReminderDeliveryMapper reminderDeliveryMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private FeishuService feishuService;

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private ReminderFeishuMessageMapper reminderFeishuMessageMapper;

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS, initialDelay = SCAN_INITIAL_DELAY_MS)
    public void runScheduledScan() {
        runReminderScan(false);
    }

    /** 规则变更、服务启动或重新启用通知时立即补发历史欠账（合并为单条）。 */
    public void runCatchUpScan() {
        try {
            runReminderScan(true);
        } catch (Exception e) {
            log.error("提醒补发扫描异常: {}", e.getMessage(), e);
        }
    }

    private void runReminderScan(boolean catchUpContext) {
        if (!pluginConfigService.isNotificationEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ReminderRule> rules = reminderRuleMapper.selectEnabled();
        if (rules.isEmpty()) {
            return;
        }

        LinkedHashSet<String> allStatuses = new LinkedHashSet<>();
        Map<String, List<String>> recipientsByRule = new HashMap<>();
        for (ReminderRule rule : rules) {
            List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
            if (statuses != null) {
                for (String status : statuses) {
                    if (status != null && !status.trim().isEmpty()) {
                        allStatuses.add(status.trim());
                    }
                }
            }
            List<String> configured = reminderRuleMapper.selectRecipientUserIds(rule.getId());
            recipientsByRule.put(rule.getId(),
                    configured != null ? configured : Collections.emptyList());
        }

        List<Task> allTasks = allStatuses.isEmpty()
                ? Collections.emptyList()
                : taskMapper.selectTasksByStatuses(new ArrayList<>(allStatuses));
        List<String> taskIds = new ArrayList<>(allTasks.size());
        for (Task task : allTasks) {
            if (task != null && task.getTaskId() != null && !task.getTaskId().isEmpty()) {
                taskIds.add(task.getTaskId());
            }
        }

        Map<String, Map<String, Set<String>>> deliveredByRule = new HashMap<>();
        for (ReminderRule rule : rules) {
            deliveredByRule.put(rule.getId(), loadDeliveredIndex(rule.getId(), taskIds));
        }

        Map<String, User> userCache = new HashMap<>();
        Map<String, Map<String, List<ReminderHit>>> userLocaleBuckets = new LinkedHashMap<>();
        Map<String, RuleRunStats> ruleStats = new LinkedHashMap<>();

        for (ReminderRule rule : rules) {
            ruleStats.put(rule.getId(), new RuleRunStats(rule));
            collectHitsForRule(
                    rule,
                    now,
                    allTasks,
                    recipientsByRule.get(rule.getId()),
                    deliveredByRule.get(rule.getId()),
                    userCache,
                    userLocaleBuckets,
                    ruleStats.get(rule.getId()));
        }

        int totalDeliveries = 0;
        for (Map.Entry<String, Map<String, List<ReminderHit>>> userEntry : userLocaleBuckets.entrySet()) {
            String recipientId = userEntry.getKey();
            User recipient = getUser(recipientId, userCache);
            if (recipient == null || !"active".equals(recipient.getStatus())) {
                continue;
            }
            for (Map.Entry<String, List<ReminderHit>> localeEntry : userEntry.getValue().entrySet()) {
                String locale = localeEntry.getKey();
                List<ReminderHit> hits = localeEntry.getValue();
                if (hits.isEmpty()) {
                    continue;
                }
                int delivered = dispatchUnified(recipient, locale, hits, userCache, ruleStats);
                if (delivered > 0) {
                    totalDeliveries += delivered;
                }
            }
        }

        for (RuleRunStats stats : ruleStats.values()) {
            if (stats.hitTasks > 0 || stats.sentDeliveries > 0) {
                reminderRuleMapper.updateLastRun(stats.rule.getId(), stats.hitTasks, stats.sentDeliveries);
            }
        }

        if (totalDeliveries > 0 || catchUpContext) {
            log.info("提醒扫描完成 catchUp={} userMessages={} ruleCount={} taskPool={}",
                    catchUpContext, totalDeliveries, rules.size(), allTasks.size());
        }
    }

    private Map<String, Set<String>> loadDeliveredIndex(String ruleId, List<String> taskIds) {
        Map<String, Set<String>> index = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return index;
        }
        List<Map<String, Object>> rows =
                reminderDeliveryMapper.selectDeliveredBucketsByRuleAndTasks(ruleId, taskIds);
        if (rows == null || rows.isEmpty()) {
            return index;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String taskId = stringVal(mapGet(row, "taskId"));
            String userId = stringVal(mapGet(row, "userId"));
            String bucket = stringVal(mapGet(row, "periodBucket"));
            if (taskId.isEmpty() || userId.isEmpty() || bucket.isEmpty()) {
                continue;
            }
            index.computeIfAbsent(deliveryKey(taskId, userId), k -> new HashSet<>()).add(bucket);
        }
        return index;
    }

    private void collectHitsForRule(ReminderRule rule,
                                    LocalDateTime now,
                                    List<Task> tasks,
                                    List<String> configuredRecipients,
                                    Map<String, Set<String>> deliveredIndex,
                                    Map<String, User> userCache,
                                    Map<String, Map<String, List<ReminderHit>>> userLocaleBuckets,
                                    RuleRunStats stats) {
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        if (statuses == null || statuses.isEmpty() || tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<String> statusSet = new HashSet<>(statuses);
        BigDecimal intervalValue = rule.getIntervalValue();
        String intervalUnit = rule.getIntervalUnit();
        long intervalMs = ReminderSupport.intervalToMillis(intervalValue, intervalUnit);

        for (Task task : tasks) {
            if (task == null || task.getStatus() == null || !statusSet.contains(task.getStatus())) {
                continue;
            }
            if (!taskMatchesRule(task, rule, userCache)) {
                continue;
            }
            LocalDateTime enteredAt = ReminderSupport.resolveStatusEnteredAt(task);
            if (enteredAt == null || intervalMs <= 0) {
                continue;
            }
            long currentPeriod = ReminderSupport.computePeriodIndex(enteredAt, now, intervalMs);
            if (currentPeriod < 1) {
                continue;
            }
            if (!ReminderSupport.isScheduleTimeReached(intervalUnit, rule.getScheduleHourOfDay(), now)) {
                continue;
            }
            LocalDateTime currentDueAt = ReminderSupport.computeDueAtForPeriod(
                    enteredAt, currentPeriod, intervalValue, intervalUnit, rule.getScheduleHourOfDay());
            if (currentDueAt != null && now.isBefore(currentDueAt)) {
                continue;
            }

            Set<String> recipients = resolveRecipients(configuredRecipients, rule, task);
            String locale = ReminderLocaleSupport.resolveLocale(
                    ReminderSupport.normalizeTaskCountry(task.getPromptCountry()));

            for (String recipientId : recipients) {
                User recipient = getUser(recipientId, userCache);
                if (recipient == null || !"active".equals(recipient.getStatus())) {
                    continue;
                }
                Set<String> deliveredSet = deliveredIndex != null
                        ? deliveredIndex.getOrDefault(deliveryKey(task.getTaskId(), recipientId), Collections.emptySet())
                        : Collections.emptySet();
                List<String> undeliveredBuckets = resolveUndeliveredBuckets(currentPeriod, deliveredSet);
                if (undeliveredBuckets.isEmpty()) {
                    continue;
                }
                stats.hitTasks += undeliveredBuckets.size();
                userLocaleBuckets
                        .computeIfAbsent(recipientId, k -> new LinkedHashMap<>())
                        .computeIfAbsent(locale, k -> new ArrayList<>())
                        .add(new ReminderHit(rule, task, recipient, undeliveredBuckets));
            }
        }
    }

    /**
     * 最近 {@link #MAX_CATCHUP_PERIODS} 个周期逐桶检查；更早欠账用 legacy 桶一次性结算。
     */
    static List<String> resolveUndeliveredBuckets(long currentPeriod, Set<String> deliveredSet) {
        Set<String> delivered = deliveredSet != null ? deliveredSet : Collections.emptySet();
        List<String> undelivered = new ArrayList<>();
        long scanFrom = Math.max(1L, currentPeriod - MAX_CATCHUP_PERIODS + 1);
        if (scanFrom > 1L) {
            String legacyBucket = LEGACY_BUCKET_PREFIX + scanFrom;
            if (!delivered.contains(legacyBucket) && hasLegacyDebt(scanFrom, delivered)) {
                undelivered.add(legacyBucket);
            }
        }
        for (long period = scanFrom; period <= currentPeriod; period++) {
            String bucket = String.valueOf(period);
            if (!delivered.contains(bucket)) {
                undelivered.add(bucket);
            }
        }
        return undelivered;
    }

    private static boolean hasLegacyDebt(long scanFrom, Set<String> delivered) {
        long coveredBefore = 0L;
        for (String bucket : delivered) {
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            if (bucket.startsWith(LEGACY_BUCKET_PREFIX)) {
                return false;
            }
            if (!isNumericBucket(bucket)) {
                continue;
            }
            try {
                long period = Long.parseLong(bucket);
                if (period >= 1L && period < scanFrom) {
                    coveredBefore++;
                }
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return coveredBefore < (scanFrom - 1L);
    }

    private static boolean isNumericBucket(String bucket) {
        for (int i = 0; i < bucket.length(); i++) {
            if (!Character.isDigit(bucket.charAt(i))) {
                return false;
            }
        }
        return !bucket.isEmpty();
    }

    private static String deliveryKey(String taskId, String userId) {
        return taskId + '\0' + userId;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Object mapGet(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int dispatchUnified(User recipient,
                                String locale,
                                List<ReminderHit> hits,
                                Map<String, User> userCache,
                                Map<String, RuleRunStats> ruleStats) {
        if (hits == null || hits.isEmpty()) {
            return 0;
        }

        for (ReminderHit hit : hits) {
            for (String periodBucket : hit.periodBuckets) {
                recordDelivery(hit.rule, hit.task, recipient.getId(), periodBucket, recipient);
                RuleRunStats stats = ruleStats.get(hit.rule.getId());
                if (stats != null) {
                    stats.sentDeliveries++;
                }
            }
        }

        List<Task> distinctTasks = hits.stream()
                .map(h -> h.task)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Task::getTaskId, t -> t, (a, b) -> a, LinkedHashMap::new),
                        m -> new ArrayList<>(m.values())))
                .stream()
                .sorted(Comparator.comparing(
                        t -> t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Map<String, List<Task>> tasksByRule = new LinkedHashMap<>();
        Map<String, ReminderRule> rulesById = new LinkedHashMap<>();
        for (ReminderHit hit : hits) {
            rulesById.put(hit.rule.getId(), hit.rule);
            List<Task> ruleTasks = tasksByRule.computeIfAbsent(hit.rule.getId(), k -> new ArrayList<>());
            boolean exists = false;
            for (Task existing : ruleTasks) {
                if (hit.task.getTaskId().equals(existing.getTaskId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                ruleTasks.add(hit.task);
            }
        }

        Task latest = distinctTasks.get(0);
        LocalDateTime latestTime = ReminderSupport.resolveStatusEnteredAt(latest);
        boolean recipientIsOperator = distinctTasks.stream()
                .allMatch(t -> recipient.getId().equals(t.getUserId()));

        List<String> creatorNameList = collectCreatorNames(distinctTasks, userCache);
        String creatorNames = ReminderMessageBuilder.joinCreatorNames(creatorNameList, locale);

        String title = ReminderMessageBuilder.aggregatedNotificationTitle(locale, tasksByRule.size());
        String body = ReminderMessageBuilder.buildAggregatedBody(
                locale,
                recipient,
                recipientIsOperator,
                distinctTasks.size(),
                rulesById,
                tasksByRule,
                latest,
                latestTime,
                creatorNames);

        String siteLink = ReminderSupport.buildPcTaskLink(
                feishuProperties.getFrontendLoginUrl(), latest.getTaskId());
        String periodBucket = ReminderSupport.aggregatePeriodBucket(locale);

        NotificationContentVars contentVars = buildAggregateContentVars(
                distinctTasks.size(),
                tasksByRule.size(),
                latest,
                latestTime,
                ReminderMessageBuilder.displayName(recipient),
                creatorNameList,
                recipientIsOperator);

        SiteNotificationReplaceResult replaced = userNotificationService.replaceSiteNotification(
                recipient.getId(),
                ReminderSupport.AGGREGATE_RULE_ID,
                periodBucket,
                title,
                body,
                siteLink,
                contentVars.toJson());

        String webLoginLink = ReminderSupport.buildFeishuWebLoginTaskLink(
                feishuProperties.getApiBaseUrl(), latest.getTaskId());
        String webApplink = ReminderSupport.buildFeishuWebApplink(webLoginLink);
        String miniprogramLink = ReminderSupport.buildMiniprogramTaskApplink(
                feishuProperties.getAppId(), latest.getTaskId());

        sendFeishuAggregated(locale, recipient, title, body, webLoginLink, webApplink, miniprogramLink, replaced);
        return 1;
    }

    private void sendFeishuAggregated(String locale,
                                      User recipient,
                                      String title,
                                      String body,
                                      String webLoginLink,
                                      String webApplink,
                                      String miniprogramLink,
                                      SiteNotificationReplaceResult replaced) {
        String feishuUserId = recipient.getFeishuUserId();
        if (feishuUserId == null || feishuUserId.trim().isEmpty()) {
            return;
        }
        String localeKey = locale != null && !locale.trim().isEmpty()
                ? locale.trim()
                : ReminderLocaleSupport.DEFAULT_LOCALE;
        String previousMessageId = reminderFeishuMessageMapper.selectMessageId(
                recipient.getId(), ReminderSupport.AGGREGATE_RULE_ID, localeKey);
        if (previousMessageId == null || previousMessageId.trim().isEmpty()) {
            previousMessageId = replaced != null ? replaced.getPreviousFeishuMessageId() : null;
        }
        if (previousMessageId != null && !previousMessageId.trim().isEmpty()) {
            try {
                feishuService.recallMessage(previousMessageId);
            } catch (Exception e) {
                log.warn("撤回上一条飞书汇总提醒失败 userId={} messageId={}: {}",
                        recipient.getId(), previousMessageId, e.getMessage());
            }
        }
        try {
            JSONObject card = buildFeishuCard(title, body, webLoginLink, webApplink, miniprogramLink, localeKey);
            String messageId = feishuService.sendCardMessage(feishuUserId, card);
            if (messageId != null && !messageId.trim().isEmpty()) {
                reminderFeishuMessageMapper.upsertMessageId(
                        recipient.getId(), ReminderSupport.AGGREGATE_RULE_ID, localeKey, messageId);
                if (replaced != null) {
                    userNotificationService.updateFeishuMessageId(
                            replaced.getNotificationId(), recipient.getId(), messageId);
                }
                log.info("飞书汇总提醒已发送 userId={} messageId={}", recipient.getId(), messageId);
            }
        } catch (Exception e) {
            log.warn("飞书汇总提醒发送失败 userId={} feishuUserId={}: {}",
                    recipient.getId(), feishuUserId, e.getMessage());
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

    private boolean taskMatchesRule(Task task, ReminderRule rule, Map<String, User> userCache) {
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        if (statuses == null || !statuses.contains(task.getStatus())) {
            return false;
        }
        User creator = getUser(task.getUserId(), userCache);
        List<String> creatorRoles = creator != null
                ? userRoleService.getRoleKeysForUserId(creator.getId()) : Collections.emptyList();
        return ReminderSupport.taskMatchesRuleScope(
                task,
                creator,
                creatorRoles,
                ReminderSupport.parseScopeList(rule.getScopeCountriesJson()),
                ReminderSupport.parseScopeList(rule.getScopeRolesJson()));
    }

    private Set<String> resolveRecipients(List<String> configuredRecipients, ReminderRule rule, Task task) {
        Set<String> recipients = new LinkedHashSet<>(
                configuredRecipients != null ? configuredRecipients : Collections.emptyList());
        if (rule.isIncludeTaskCreator() && task.getUserId() != null) {
            recipients.add(task.getUserId());
        }
        return recipients;
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
        delivery.setFeishuStatus(feishuOk ? "sent" : "skipped");
        try {
            reminderDeliveryMapper.insertDeliveryIgnore(delivery);
        } catch (Exception e) {
            log.debug("提醒投递记录写入失败 ruleId={} taskId={} userId={} period={}: {}",
                    rule.getId(), task.getTaskId(), recipientId, periodBucket, e.getMessage());
        }
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

    private static NotificationContentVars buildAggregateContentVars(int pendingCount,
                                                                     int ruleCount,
                                                                     Task latest,
                                                                     LocalDateTime latestTime,
                                                                     String recipientName,
                                                                     List<String> creatorNames,
                                                                     boolean recipientIsOperator) {
        NotificationContentVars contentVars = new NotificationContentVars();
        contentVars.setPendingCount(pendingCount);
        contentVars.setRecipientName(recipientName);
        contentVars.setCreatorNames(creatorNames != null ? creatorNames : new ArrayList<>());
        contentVars.setRecipientIsOperator(recipientIsOperator);
        contentVars.setLatestTaskId(latest.getTaskId());
        contentVars.setLatestTaskTime(latestTime != null ? latestTime.format(CONTENT_TIME) : null);
        contentVars.setTaskStatus(latest.getStatus());
        contentVars.setIntervalValue(String.valueOf(ruleCount));
        contentVars.setIntervalUnit("rules");
        return contentVars;
    }

    private static final class ReminderHit {
        private final ReminderRule rule;
        private final Task task;
        private final User recipient;
        private final List<String> periodBuckets;

        private ReminderHit(ReminderRule rule, Task task, User recipient, List<String> periodBuckets) {
            this.rule = rule;
            this.task = task;
            this.recipient = recipient;
            this.periodBuckets = periodBuckets;
        }
    }

    private static final class RuleRunStats {
        private final ReminderRule rule;
        private int hitTasks;
        private int sentDeliveries;

        private RuleRunStats(ReminderRule rule) {
            this.rule = rule;
        }
    }
}
