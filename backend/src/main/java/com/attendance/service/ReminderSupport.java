package com.attendance.service;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.attendance.entity.Task;
import com.attendance.entity.User;

public final class ReminderSupport {

    public static final String PERMISSION_KEY = "reminderConfig";
    public static final String NOTIFICATION_CONFIG_KEY = "notification_enabled";

    /** 按用户汇总提醒时站内/飞书合并键前缀（后缀为 locale，如 summary:fr-FR） */
    public static final String AGGREGATE_PERIOD_BUCKET = "summary";

    public static String aggregatePeriodBucket(String locale) {
        String resolved = locale != null ? locale.trim() : "";
        if (resolved.isEmpty()) {
            resolved = ReminderLocaleSupport.DEFAULT_LOCALE;
        }
        return AGGREGATE_PERIOD_BUCKET + ":" + resolved;
    }

    public static final String DEFAULT_TEMPLATE =
            "【考勤待核对提醒】\n\n"
                    + "您有 {pendingCount} 个任务处于「待核对」状态，已超过 {threshold} 未处理。\n\n"
                    + "最近任务：{latestTaskId}\n"
                    + "更新时间：{latestTaskTime}\n\n"
                    + "请登录系统及时完成核对。";

    /** 任务操作者本人（创建者）默认文案，与 {@link #DEFAULT_TEMPLATE} 相同 */
    public static final String DEFAULT_TEMPLATE_OPERATOR = DEFAULT_TEMPLATE;

    /** 非任务操作者（配置的督办提醒人）默认文案 */
    public static final String DEFAULT_TEMPLATE_SUPERVISOR =
            "【考勤待核对督办】\n\n"
                    + "{recipientName}，您好\n\n"
                    + "共有 {pendingCount} 个任务处于「{taskStatus}」状态，已超过 {threshold} 未处理。\n"
                    + "涉及操作者：{taskCreatorNames}\n\n"
                    + "最近任务：{latestTaskId}\n"
                    + "更新时间：{latestTaskTime}\n\n"
                    + "请关注并督促相关人员登录系统完成核对。";

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final BigDecimal MIN_INTERVAL = new BigDecimal("0.1");

    private ReminderSupport() {
    }

    public static BigDecimal normalizeIntervalValue(BigDecimal value) {
        if (value == null) {
            return MIN_INTERVAL;
        }
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
        return rounded.compareTo(MIN_INTERVAL) < 0 ? MIN_INTERVAL : rounded;
    }

    public static boolean isValidIntervalValue(BigDecimal value) {
        if (value == null) {
            return false;
        }
        if (value.compareTo(MIN_INTERVAL) < 0) {
            return false;
        }
        return value.setScale(1, RoundingMode.HALF_UP).compareTo(value) == 0;
    }

    public static String normalizeIntervalUnit(String unit) {
        if (unit == null) {
            return "day";
        }
        return unit.trim().toLowerCase();
    }

    public static long intervalToMillis(BigDecimal value, String unit) {
        BigDecimal v = normalizeIntervalValue(value);
        String u = normalizeIntervalUnit(unit);
        BigDecimal millis;
        switch (u) {
            case "minute":
                millis = v.multiply(BigDecimal.valueOf(60_000L));
                break;
            case "hour":
                millis = v.multiply(BigDecimal.valueOf(3_600_000L));
                break;
            case "week":
                millis = v.multiply(BigDecimal.valueOf(7L * 24L * 3_600_000L));
                break;
            case "day":
            default:
                millis = v.multiply(BigDecimal.valueOf(24L * 3_600_000L));
                break;
        }
        return millis.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public static String formatIntervalDisplay(BigDecimal value) {
        BigDecimal v = normalizeIntervalValue(value);
        if (v.stripTrailingZeros().scale() <= 0) {
            return v.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        }
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    public static String buildMiniprogramTaskPath(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        return "pages/result/index?id=" + taskId.trim();
    }

    /**
     * 飞书消息卡片按钮：打开考勤小程序任务详情页。
     */
    public static String buildMiniprogramTaskApplink(String appId, String taskId) {
        if (appId == null || appId.trim().isEmpty()) {
            return null;
        }
        String path = buildMiniprogramTaskPath(taskId);
        if (path == null) {
            return null;
        }
        try {
            return "https://applink.feishu.cn/client/mini_program/open?appId="
                    + URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8.name())
                    + "&path="
                    + URLEncoder.encode(path, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static String buildPcTaskLink(String frontendBaseUrl, String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        String base = frontendBaseUrl;
        if (base == null || base.trim().isEmpty()) {
            base = "http://localhost:5175/attendance/";
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + "tasks/" + taskId.trim();
    }

    /**
     * 登录后跳转的前端路径（相对 /attendance/ 路由）。
     */
    public static String buildTaskRedirectPath(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        return "/tasks/" + taskId.trim();
    }

    public static String sanitizePostLoginRedirect(String redirect) {
        if (redirect == null || redirect.trim().isEmpty()) {
            return null;
        }
        String path = redirect.trim();
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            return null;
        }
        if (path.startsWith("/tasks/") || "/home".equals(path) || path.startsWith("/task-records")) {
            return path;
        }
        return null;
    }

    /**
     * 飞书 PC / 内置浏览器：先 OAuth 以当前飞书用户登录，再打开任务页。
     */
    public static String buildFeishuWebLoginTaskLink(String apiBaseUrl, String taskId) {
        String redirectPath = buildTaskRedirectPath(taskId);
        if (redirectPath == null) {
            return null;
        }
        String base = apiBaseUrl;
        if (base == null || base.trim().isEmpty()) {
            base = "http://localhost:8080/attendance/api";
        }
        base = base.replaceAll("/+$", "");
        try {
            return base + "/feishu-auth/login?redirect="
                    + URLEncoder.encode(redirectPath, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /** 在飞书客户端内打开网页（保留飞书登录态，便于 OAuth 免登） */
    public static String buildFeishuWebApplink(String webUrl) {
        if (webUrl == null || webUrl.trim().isEmpty()) {
            return null;
        }
        try {
            return "https://applink.feishu.cn/client/web_url/open?url="
                    + URLEncoder.encode(webUrl.trim(), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static String formatThreshold(BigDecimal value, String unit) {
        String display = formatIntervalDisplay(value);
        switch (normalizeIntervalUnit(unit)) {
            case "minute":
                return display + " 分钟";
            case "hour":
                return display + " 小时";
            case "week":
                return display + " 周";
            case "day":
            default:
                return display + " 天";
        }
    }

    public static String formatStatusLabel(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "processed":
                return "待核对";
            case "processing":
                return "识别中";
            case "confirmed":
                return "已确认";
            case "failed":
                return "失败";
            case "cancelled":
                return "已取消";
            default:
                return status;
        }
    }

    public static boolean supportsScheduleHour(String unit) {
        String normalized = normalizeIntervalUnit(unit);
        return "day".equals(normalized) || "week".equals(normalized);
    }

    public static Integer normalizeScheduleHour(Integer hour, String unit) {
        if (!supportsScheduleHour(unit)) {
            return null;
        }
        if (hour == null) {
            return null;
        }
        int value = hour;
        if (value < 0) {
            value = 0;
        }
        if (value > 23) {
            value = 23;
        }
        return value;
    }

    /**
     * day/week 周期：达到滞后阈值后，仅在当天该小时及之后发送（调度每 15 分钟扫描）。
     */
    public static boolean isScheduleTimeReached(String unit, Integer scheduleHour, LocalDateTime now) {
        if (!supportsScheduleHour(unit)) {
            return true;
        }
        Integer hour = normalizeScheduleHour(scheduleHour, unit);
        if (hour == null) {
            return true;
        }
        if (now == null) {
            return false;
        }
        return now.getHour() >= hour;
    }

    public static String formatScheduleHour(Integer hour) {
        if (hour == null) {
            return "";
        }
        int value = Math.max(0, Math.min(23, hour));
        return String.format("%02d:00", value);
    }

    public static long computePeriodIndex(LocalDateTime statusEnteredAt, LocalDateTime now, long intervalMs) {
        if (statusEnteredAt == null || intervalMs <= 0) {
            return 0;
        }
        long elapsed = Duration.between(statusEnteredAt, now).toMillis();
        if (elapsed < intervalMs) {
            return 0;
        }
        return elapsed / intervalMs;
    }

    public static String renderTemplate(String template, Map<String, String> vars) {
        String result = template == null ? "" : template;
        if (vars == null) {
            return result;
        }
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String key = "{" + e.getKey() + "}";
            result = result.replace(key, e.getValue() != null ? e.getValue() : "");
        }
        return result;
    }

    public static Map<String, String> baseVars(int pendingCount,
                                               BigDecimal intervalValue,
                                               String intervalUnit,
                                               String taskStatus,
                                               String latestTaskId,
                                               LocalDateTime latestTaskTime,
                                               String recipientName,
                                               String taskCreatorName,
                                               String taskCreatorNames) {
        return baseVars(pendingCount, intervalValue, intervalUnit, taskStatus, latestTaskId,
                latestTaskTime, recipientName, taskCreatorName, taskCreatorNames,
                ReminderLocaleSupport.DEFAULT_LOCALE);
    }

    public static Map<String, String> baseVars(int pendingCount,
                                               BigDecimal intervalValue,
                                               String intervalUnit,
                                               String taskStatus,
                                               String latestTaskId,
                                               LocalDateTime latestTaskTime,
                                               String recipientName,
                                               String taskCreatorName,
                                               String taskCreatorNames,
                                               String locale) {
        Map<String, String> vars = new HashMap<>();
        vars.put("pendingCount", String.valueOf(pendingCount));
        vars.put("threshold", ReminderLocaleSupport.formatThreshold(intervalValue, intervalUnit, locale));
        vars.put("taskStatus", ReminderLocaleSupport.formatStatusLabel(taskStatus, locale));
        vars.put("latestTaskId", latestTaskId != null ? latestTaskId : "-");
        vars.put("latestTaskTime", latestTaskTime != null ? latestTaskTime.format(DISPLAY_TIME) : "-");
        vars.put("recipientName", recipientName != null ? recipientName : "");
        vars.put("taskCreatorName", taskCreatorName != null ? taskCreatorName : "");
        vars.put("taskCreatorNames", taskCreatorNames != null && !taskCreatorNames.isEmpty()
                ? taskCreatorNames
                : (taskCreatorName != null ? taskCreatorName : "-"));
        return vars;
    }

    /**
     * 按收件人身份选择文案：全部为本人创建的任务用操作者模板，否则用督办模板。
     */
    public static String resolveMessageTemplate(String operatorTemplate,
                                              String supervisorTemplate,
                                              boolean recipientIsTaskOperator) {
        if (recipientIsTaskOperator) {
            String t = operatorTemplate != null ? operatorTemplate.trim() : "";
            return t.isEmpty() ? DEFAULT_TEMPLATE_OPERATOR : operatorTemplate;
        }
        String t = supervisorTemplate != null ? supervisorTemplate.trim() : "";
        return t.isEmpty() ? DEFAULT_TEMPLATE_SUPERVISOR : supervisorTemplate;
    }

    public static List<String> parseScopeList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = JSON.parseArray(json, String.class);
        return list != null ? list : Collections.emptyList();
    }

    public static String normalizeTaskCountry(String promptCountry) {
        if (promptCountry == null || promptCountry.trim().isEmpty()) {
            return "default";
        }
        return promptCountry.trim();
    }

    /**
     * 规则范围：工作国家匹配 tasks.prompt_country；角色匹配任务创建者 users.role。
     * scope 列表为空表示不限制该维度。
     */
    public static boolean taskMatchesRuleScope(Task task, User creator, List<String> scopeCountries, List<String> scopeRoles) {
        if (scopeCountries != null && !scopeCountries.isEmpty()) {
            Set<String> allowed = scopeCountries.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            if (!allowed.isEmpty()) {
                String taskCountry = normalizeTaskCountry(task != null ? task.getPromptCountry() : null).toLowerCase();
                if (!allowed.contains(taskCountry)) {
                    return false;
                }
            }
        }
        if (scopeRoles != null && !scopeRoles.isEmpty()) {
            Set<String> allowed = scopeRoles.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            if (!allowed.isEmpty()) {
                String role = creator != null && creator.getRole() != null ? creator.getRole().trim() : "";
                if (role.isEmpty() || !allowed.contains(role)) {
                    return false;
                }
            }
        }
        return true;
    }
}
