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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerService.class);
    private static final DateTimeFormatter CONTENT_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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
    private UserNotificationService userNotificationService;

    @Autowired
    private FeishuService feishuService;

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private ReminderFeishuMessageMapper reminderFeishuMessageMapper;

    @Scheduled(fixedDelay = 900_000, initialDelay = 180_000)
    public void runReminders() {
        if (!pluginConfigService.isNotificationEnabled()) {
            return;
        }
        List<ReminderRule> rules = reminderRuleMapper.selectEnabled();
        if (rules.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, User> userCache = new HashMap<>();
        for (ReminderRule rule : rules) {
            try {
                processRule(rule, now, userCache);
            } catch (Exception e) {
                log.error("提醒规则执行失败 ruleId={}", rule.getId(), e);
            }
        }
    }

    private void processRule(ReminderRule rule, LocalDateTime now, Map<String, User> userCache) {
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        List<String> ruleRecipients = reminderRuleMapper.selectRecipientUserIds(rule.getId());
        if (ruleRecipients == null || ruleRecipients.isEmpty()) {
            return;
        }
        Set<String> ruleRecipientSet = new HashSet<>(ruleRecipients);
        long intervalMs = ReminderSupport.intervalToMillis(rule.getIntervalValue(), rule.getIntervalUnit());

        List<Task> tasks = taskMapper.selectTasksByStatuses(statuses);
        int hitCount = 0;
        int sentCount = 0;

        // userId -> countryCode -> tasks pending delivery in this run (per locale message)
        Map<String, Map<String, List<Task>>> userTaskAgg = new LinkedHashMap<>();

        for (Task task : tasks) {
            User creator = getUser(task.getUserId(), userCache);
            if (!ReminderSupport.taskMatchesRuleScope(
                    task,
                    creator,
                    ReminderSupport.parseScopeList(rule.getScopeCountriesJson()),
                    ReminderSupport.parseScopeList(rule.getScopeRolesJson()))) {
                continue;
            }
            LocalDateTime enteredAt = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
            long periodIndex = ReminderSupport.computePeriodIndex(enteredAt, now, intervalMs);
            if (periodIndex < 1) {
                continue;
            }
            String periodBucket = String.valueOf(periodIndex);
            Set<String> recipients = new HashSet<>(ruleRecipientSet);
            if (rule.isIncludeTaskCreator() && task.getUserId() != null) {
                recipients.add(task.getUserId());
            }
            boolean taskHit = false;
            for (String recipientId : recipients) {
                User recipient = getUser(recipientId, userCache);
                if (recipient == null || !"active".equals(recipient.getStatus())) {
                    if (recipient != null) {
                        log.debug("跳过非活跃用户提醒 userId={} taskId={}", recipientId, task.getTaskId());
                    }
                    continue;
                }
                if (reminderDeliveryMapper.existsDelivery(rule.getId(), task.getTaskId(), recipientId, periodBucket) > 0) {
                    continue;
                }
                taskHit = true;
                recordDelivery(rule, task, recipientId, periodBucket, recipient);
                sentCount++;
                String countryKey = ReminderSupport.normalizeTaskCountry(task.getPromptCountry());
                userTaskAgg
                        .computeIfAbsent(recipientId, k -> new LinkedHashMap<>())
                        .computeIfAbsent(countryKey, k -> new ArrayList<>())
                        .add(task);
            }
            if (taskHit) {
                hitCount++;
            }
        }

        Map<String, String> operatorLocales = ReminderLocaleSupport.parseTemplateMap(rule.getMessageTemplateLocalesJson());
        Map<String, String> supervisorLocales = ReminderLocaleSupport.parseTemplateMap(
                rule.getMessageTemplateSupervisorLocalesJson());
        if (operatorLocales.isEmpty() && rule.getMessageTemplate() != null) {
            operatorLocales.put(ReminderLocaleSupport.DEFAULT_LOCALE, rule.getMessageTemplate());
        }
        if (supervisorLocales.isEmpty() && rule.getMessageTemplateSupervisor() != null) {
            supervisorLocales.put(ReminderLocaleSupport.DEFAULT_LOCALE, rule.getMessageTemplateSupervisor());
        }

        for (Map.Entry<String, Map<String, List<Task>>> entry : userTaskAgg.entrySet()) {
            String recipientId = entry.getKey();
            User recipient = getUser(recipientId, userCache);
            if (recipient == null) {
                continue;
            }
            for (Map.Entry<String, List<Task>> countryEntry : entry.getValue().entrySet()) {
                List<Task> userTasks = countryEntry.getValue();
                if (userTasks == null || userTasks.isEmpty()) {
                    continue;
                }
                String countryKey = countryEntry.getKey();
                String locale = ReminderLocaleSupport.resolveLocale(countryKey);
                String periodBucket = ReminderSupport.aggregatePeriodBucket(locale);

                userTasks.sort(Comparator.comparing(
                        t -> t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())));
                Task latest = userTasks.get(0);
                LocalDateTime latestTime = latest.getUpdatedAt() != null ? latest.getUpdatedAt() : latest.getCreatedAt();
                String statusLabel = statuses.size() == 1 ? statuses.get(0) : "processed";
                User creator = getUser(latest.getUserId(), userCache);
                boolean recipientIsOperator = userTasks.stream()
                        .allMatch(t -> recipientId.equals(t.getUserId()));
                List<String> creatorNameList = collectCreatorNames(userTasks, userCache);
                String creatorNames = joinCreatorNames(creatorNameList, locale);
                Map<String, String> vars = ReminderSupport.baseVars(
                        userTasks.size(),
                        rule.getIntervalValue(),
                        rule.getIntervalUnit(),
                        statusLabel,
                        latest.getTaskId(),
                        latestTime,
                        displayName(recipient),
                        creator != null ? displayName(creator) : "",
                        creatorNames,
                        locale);
                String template = ReminderLocaleSupport.pickLocalizedTemplate(
                        operatorLocales,
                        supervisorLocales,
                        rule.getMessageTemplate(),
                        rule.getMessageTemplateSupervisor(),
                        locale,
                        recipientIsOperator);
                String body = ReminderSupport.renderTemplate(template, vars);
                String title = ReminderLocaleSupport.notificationTitlePrefix(locale) + rule.getName();
                String siteLink = ReminderSupport.buildPcTaskLink(
                        feishuProperties.getFrontendLoginUrl(), latest.getTaskId());
                String feishuLink = ReminderSupport.buildMiniprogramTaskApplink(
                        feishuProperties.getAppId(), latest.getTaskId());
                NotificationContentVars contentVars = buildContentVars(
                        userTasks.size(),
                        rule,
                        statusLabel,
                        latest,
                        latestTime,
                        displayName(recipient),
                        creator != null ? displayName(creator) : "",
                        creatorNameList,
                        recipientIsOperator);
                SiteNotificationReplaceResult replaced = userNotificationService.replaceSiteNotification(
                        recipientId, rule.getId(), periodBucket, title, body, siteLink, contentVars.toJson());
                String webLoginLink = ReminderSupport.buildFeishuWebLoginTaskLink(
                        feishuProperties.getApiBaseUrl(), latest.getTaskId());
                String webApplink = ReminderSupport.buildFeishuWebApplink(webLoginLink);
                sendFeishuIfPossible(rule.getId(), locale, recipient, title, body,
                        webLoginLink, webApplink, feishuLink, replaced);
            }
        }

        reminderRuleMapper.updateLastRun(rule.getId(), hitCount, sentCount);
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

    private static String displayName(User user) {
        if (user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
            return user.getRealName().trim();
        }
        return user.getUsername();
    }

    private List<String> collectCreatorNames(List<Task> tasks, Map<String, User> userCache) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Task task : tasks) {
            User creator = getUser(task.getUserId(), userCache);
            if (creator != null) {
                names.add(displayName(creator));
            }
        }
        return new ArrayList<>(names);
    }

    private static String joinCreatorNames(List<String> names, String locale) {
        if (names == null || names.isEmpty()) {
            return "-";
        }
        return String.join(ReminderLocaleSupport.nameSeparator(locale), names);
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
}
