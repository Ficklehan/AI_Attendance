package com.attendance.service;

import com.attendance.entity.ReminderRule;
import com.attendance.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 提醒通知标题/正文渲染（Scheduler 与 NotificationLocalizationService 共用） */
public final class ReminderMessageBuilder {

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private ReminderMessageBuilder() {
    }

    public static String displayName(User user) {
        if (user == null) {
            return "";
        }
        if (user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
            return user.getRealName().trim();
        }
        return user.getUsername();
    }

    public static String joinCreatorNames(List<String> names, String locale) {
        if (names == null || names.isEmpty()) {
            return "-";
        }
        return String.join(ReminderLocaleSupport.nameSeparator(locale), names);
    }

    public static LocaleTemplates resolveLocaleTemplates(ReminderRule rule) {
        Map<String, String> operatorLocales = ReminderLocaleSupport.parseTemplateMap(
                rule.getMessageTemplateLocalesJson());
        Map<String, String> supervisorLocales = ReminderLocaleSupport.parseTemplateMap(
                rule.getMessageTemplateSupervisorLocalesJson());
        if (operatorLocales.isEmpty() && rule.getMessageTemplate() != null) {
            operatorLocales.put(ReminderLocaleSupport.DEFAULT_LOCALE, rule.getMessageTemplate());
        }
        if (supervisorLocales.isEmpty() && rule.getMessageTemplateSupervisor() != null) {
            supervisorLocales.put(ReminderLocaleSupport.DEFAULT_LOCALE, rule.getMessageTemplateSupervisor());
        }
        return new LocaleTemplates(operatorLocales, supervisorLocales);
    }

    public static String pickTemplate(LocaleTemplates templates,
                                    ReminderRule rule,
                                    String locale,
                                    boolean recipientIsOperator) {
        return ReminderLocaleSupport.pickLocalizedTemplate(
                templates.operatorLocales,
                templates.supervisorLocales,
                rule.getMessageTemplate(),
                rule.getMessageTemplateSupervisor(),
                locale,
                recipientIsOperator);
    }

    public static String renderBody(int pendingCount,
                                    BigDecimal intervalValue,
                                    String intervalUnit,
                                    String taskStatus,
                                    String latestTaskId,
                                    LocalDateTime latestTime,
                                    String recipientName,
                                    String taskCreatorName,
                                    String creatorNames,
                                    String template,
                                    String locale) {
        Map<String, String> vars = ReminderSupport.baseVars(
                pendingCount,
                intervalValue,
                intervalUnit,
                taskStatus,
                latestTaskId,
                latestTime,
                recipientName,
                taskCreatorName,
                creatorNames,
                locale);
        return ReminderSupport.renderTemplate(template, vars);
    }

    public static String notificationTitle(ReminderRule rule, String locale) {
        return ReminderLocaleSupport.notificationTitlePrefix(locale) + rule.getName();
    }

    public static LocalDateTime parseLatestTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), ISO_LOCAL);
        } catch (Exception e) {
            return null;
        }
    }

    public static BigDecimal parseIntervalValue(String fromVars, BigDecimal ruleValue) {
        if (fromVars != null && !fromVars.trim().isEmpty()) {
            try {
                return new BigDecimal(fromVars.trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ruleValue;
    }

    public static final class LocaleTemplates {
        public final Map<String, String> operatorLocales;
        public final Map<String, String> supervisorLocales;

        public LocaleTemplates(Map<String, String> operatorLocales, Map<String, String> supervisorLocales) {
            this.operatorLocales = operatorLocales;
            this.supervisorLocales = supervisorLocales;
        }
    }
}
