package com.attendance.service;

import com.attendance.dto.NotificationContentVars;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.UserNotification;
import com.attendance.mapper.ReminderRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class NotificationLocalizationService {

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private ReminderRuleMapper reminderRuleMapper;

    @Autowired
    private LegacyNotificationVarsRebuilder legacyNotificationVarsRebuilder;

    public void applyLocale(UserNotificationDTOHolder dto, UserNotification source, String requestLocale) {
        if (dto == null || source == null) {
            return;
        }
        if (source.getRuleId() == null || source.getRuleId().trim().isEmpty()) {
            return;
        }
        String locale = ReminderLocaleSupport.normalizeLocale(requestLocale);
        ReminderRule rule = reminderRuleMapper.selectById(source.getRuleId());
        if (rule == null) {
            return;
        }

        NotificationContentVars vars = NotificationContentVars.fromJson(source.getContentVarsJson());
        if (vars == null) {
            vars = legacyNotificationVarsRebuilder.rebuild(source, rule);
        }
        if (vars == null) {
            dto.setTitle(ReminderLocaleSupport.notificationTitlePrefix(locale) + rule.getName());
            return;
        }

        Map<String, String> operatorLocales = ReminderLocaleSupport.parseTemplateMap(rule.getMessageTemplateLocalesJson());
        Map<String, String> supervisorLocales = ReminderLocaleSupport.parseTemplateMap(
                rule.getMessageTemplateSupervisorLocalesJson());

        String template = ReminderLocaleSupport.pickTemplateForDisplay(
                vars.isRecipientIsOperator() ? operatorLocales : supervisorLocales,
                locale,
                vars.isRecipientIsOperator());
        LocalDateTime latestTime = parseLatestTime(vars.getLatestTaskTime());
        BigDecimal intervalValue = parseIntervalValue(vars.getIntervalValue(), rule.getIntervalValue());
        String intervalUnit = vars.getIntervalUnit() != null && !vars.getIntervalUnit().trim().isEmpty()
                ? vars.getIntervalUnit().trim()
                : rule.getIntervalUnit();
        String creatorNames = formatCreatorNames(vars.getCreatorNames(), locale);
        Map<String, String> renderVars = ReminderSupport.baseVars(
                vars.getPendingCount(),
                intervalValue,
                intervalUnit,
                vars.getTaskStatus(),
                vars.getLatestTaskId(),
                latestTime,
                vars.getRecipientName(),
                vars.getTaskCreatorName(),
                creatorNames,
                locale);
        dto.setTitle(ReminderLocaleSupport.notificationTitlePrefix(locale) + rule.getName());
        dto.setBody(ReminderSupport.renderTemplate(template, renderVars));
    }

    private static LocalDateTime parseLatestTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), ISO_LOCAL);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseIntervalValue(String fromVars, BigDecimal ruleValue) {
        if (fromVars != null && !fromVars.trim().isEmpty()) {
            try {
                return new BigDecimal(fromVars.trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ruleValue;
    }

    private static String formatCreatorNames(List<String> names, String locale) {
        if (names == null || names.isEmpty()) {
            return "-";
        }
        String separator = ReminderLocaleSupport.nameSeparator(locale);
        return String.join(separator, names);
    }

    /** Mutable title/body holder for localization without extra DTO type. */
    public static final class UserNotificationDTOHolder {
        private String title;
        private String body;

        public UserNotificationDTOHolder(String title, String body) {
            this.title = title;
            this.body = body;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
