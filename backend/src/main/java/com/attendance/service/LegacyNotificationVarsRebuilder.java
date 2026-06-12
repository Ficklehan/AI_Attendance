package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.dto.NotificationContentVars;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.entity.UserNotification;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.util.NotificationLinkSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从旧版站内消息（无 content_vars）反推渲染变量，以支持切换界面语言。
 */
@Component
public class LegacyNotificationVarsRebuilder {

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern PENDING_COUNT = Pattern.compile(
            "(?:您有|共有|You have|There are|Vous avez|Il y a|Es gibt|Er zijn|Masz|Máte|Tiene|Hay)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PENDING_COUNT_ALT = Pattern.compile(
            "(\\d+)\\s*(?:个任务|task\\(s\\)|tâche|Aufgabe|tarea|ta\\(a\\)k|zadanie|úkol)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CREATOR_LINE = Pattern.compile(
            "(?:涉及操作者|Operators|Opérateurs|Bearbeiter|Operadores|Operatorzy|Operátoři)[:：]\\s*([^\\n]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern RECIPIENT_ZH = Pattern.compile("(?:^|\\n)\\s*([^\\n，,]+)，您好");
    private static final Pattern RECIPIENT_EN = Pattern.compile("(?:^|\\n)\\s*Hello\\s+([^,\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LATEST_TASK_ID = Pattern.compile(
            "(?:最近任务|Latest task|Dernière tâche|Letzte Aufgabe|Última tarea|Laatste taak|Ostatnie zadanie|Poslední úkol)[:：]\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern UPDATED_AT = Pattern.compile(
            "(?:更新时间|Updated at|Mise à jour|Aktualisiert|Actualizado|Bijgewerkt|Aktualizacja|Aktualizováno)[:：]\\s*([0-9:\\-\\s]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    public NotificationContentVars rebuild(UserNotification notification, ReminderRule rule) {
        if (notification == null || rule == null || notification.getRuleId() == null) {
            return null;
        }
        String body = notification.getBody() != null ? notification.getBody() : "";
        NotificationContentVars vars = new NotificationContentVars();
        vars.setPendingCount(extractPendingCount(body));
        vars.setIntervalValue(rule.getIntervalValue() != null
                ? rule.getIntervalValue().toPlainString()
                : "1");
        vars.setIntervalUnit(rule.getIntervalUnit());
        List<String> statuses = JSON.parseArray(rule.getTaskStatusesJson(), String.class);
        vars.setTaskStatus(statuses != null && !statuses.isEmpty() ? statuses.get(0) : "processed");

        boolean supervisor = isSupervisorBody(body);
        vars.setRecipientIsOperator(!supervisor);
        vars.setRecipientName(extractRecipientName(body));

        String taskId = NotificationLinkSupport.extractTaskId(notification.getLink());
        if (taskId == null) {
            taskId = extractLatestTaskId(body);
        }
        vars.setLatestTaskId(taskId);

        Task task = taskId != null ? taskMapper.selectTaskByTaskId(taskId) : null;
        LocalDateTime latestTime = null;
        if (task != null) {
            latestTime = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
            User creator = userMapper.selectUserById(task.getUserId());
            if (creator != null) {
                vars.setTaskCreatorName(displayName(creator));
            }
        }
        if (latestTime == null) {
            latestTime = extractUpdatedAt(body);
        }
        vars.setLatestTaskTime(latestTime != null ? latestTime.format(ISO_LOCAL) : null);

        List<String> creatorNames = extractCreatorNames(body);
        if (creatorNames.isEmpty() && vars.getTaskCreatorName() != null && !vars.getTaskCreatorName().trim().isEmpty()) {
            creatorNames = new ArrayList<>();
            creatorNames.add(vars.getTaskCreatorName().trim());
        }
        vars.setCreatorNames(creatorNames);
        return vars;
    }

    private static int extractPendingCount(String body) {
        Matcher matcher = PENDING_COUNT.matcher(body);
        if (matcher.find()) {
            return parseIntSafe(matcher.group(1), 1);
        }
        matcher = PENDING_COUNT_ALT.matcher(body);
        if (matcher.find()) {
            return parseIntSafe(matcher.group(1), 1);
        }
        return 1;
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean isSupervisorBody(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.contains("督办")
                || body.contains("Follow-up")
                || body.contains("Suivi de")
                || body.contains("Nachverfolgung")
                || body.contains("Seguimiento")
                || body.contains("Opvolging")
                || body.contains("Nadzór")
                || body.contains("Dohled")
                || CREATOR_LINE.matcher(body).find();
    }

    private static String extractRecipientName(String body) {
        Matcher zh = RECIPIENT_ZH.matcher(body);
        if (zh.find()) {
            return zh.group(1).trim();
        }
        Matcher en = RECIPIENT_EN.matcher(body);
        if (en.find()) {
            return en.group(1).trim();
        }
        return "";
    }

    private static String extractLatestTaskId(String body) {
        Matcher matcher = LATEST_TASK_ID.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static LocalDateTime extractUpdatedAt(String body) {
        Matcher matcher = UPDATED_AT.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).trim();
        try {
            return LocalDateTime.parse(raw, DISPLAY_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> extractCreatorNames(String body) {
        Matcher matcher = CREATOR_LINE.matcher(body);
        if (!matcher.find()) {
            return new ArrayList<>();
        }
        String raw = matcher.group(1).trim();
        if (raw.isEmpty() || "-".equals(raw)) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split("[、,;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String displayName(User user) {
        return ReminderMessageBuilder.displayName(user);
    }
}
