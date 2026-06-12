package com.attendance.service;

import com.attendance.dto.NotificationContentVars;
import com.attendance.entity.ReminderRule;
import com.attendance.entity.UserNotification;
import com.attendance.mapper.ReminderRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Service
public class NotificationLocalizationService {

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
            dto.setTitle(ReminderMessageBuilder.notificationTitle(rule, locale));
            return;
        }

        ReminderMessageBuilder.LocaleTemplates templates = ReminderMessageBuilder.resolveLocaleTemplates(rule);
        String template = ReminderLocaleSupport.pickTemplateForDisplay(
                vars.isRecipientIsOperator() ? templates.operatorLocales : templates.supervisorLocales,
                locale,
                vars.isRecipientIsOperator());
        LocalDateTime latestTime = ReminderMessageBuilder.parseLatestTime(vars.getLatestTaskTime());
        BigDecimal intervalValue = ReminderMessageBuilder.parseIntervalValue(
                vars.getIntervalValue(), rule.getIntervalValue());
        String intervalUnit = vars.getIntervalUnit() != null && !vars.getIntervalUnit().trim().isEmpty()
                ? vars.getIntervalUnit().trim()
                : rule.getIntervalUnit();
        String creatorNames = ReminderMessageBuilder.joinCreatorNames(vars.getCreatorNames(), locale);
        dto.setTitle(ReminderMessageBuilder.notificationTitle(rule, locale));
        dto.setBody(ReminderMessageBuilder.renderBody(
                vars.getPendingCount(),
                intervalValue,
                intervalUnit,
                vars.getTaskStatus(),
                vars.getLatestTaskId(),
                latestTime,
                vars.getRecipientName(),
                vars.getTaskCreatorName(),
                creatorNames,
                template,
                locale));
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
