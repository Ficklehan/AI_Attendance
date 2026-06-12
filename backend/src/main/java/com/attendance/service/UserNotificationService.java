package com.attendance.service;

import com.attendance.common.PageResult;
import com.attendance.dto.SiteNotificationReplaceResult;
import com.attendance.dto.response.NotificationReadResultDTO;
import com.attendance.dto.response.UserNotificationDTO;
import com.attendance.entity.Task;
import com.attendance.entity.UserNotification;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserNotificationMapper;
import com.attendance.security.SecurityUtils;
import com.attendance.util.IdGenerator;
import com.attendance.util.NotificationLinkSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserNotificationService {

    private static final int STALE_PRUNE_LIMIT = 500;

    @Autowired
    private UserNotificationMapper userNotificationMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private NotificationLocalizationService notificationLocalizationService;

    public PageResult<UserNotificationDTO> list(long current, long size, String locale) {
        String userId = SecurityUtils.getCurrentUserId();
        pruneStaleTaskNotifications(userId);
        long offset = (current - 1) * size;
        List<UserNotificationDTO> records = userNotificationMapper.selectByUser(userId, offset, size).stream()
                .map(n -> toDto(n, locale))
                .collect(Collectors.toList());
        long total = userNotificationMapper.countByUser(userId);
        return PageResult.of(records, total, current, size);
    }

    public long unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        pruneStaleTaskNotifications(userId);
        return userNotificationMapper.countUnread(userId);
    }

    public NotificationReadResultDTO markRead(String id) {
        String userId = SecurityUtils.getCurrentUserId();
        UserNotification notification = userNotificationMapper.selectById(id);
        NotificationReadResultDTO result = new NotificationReadResultDTO();
        if (notification == null || !userId.equals(notification.getUserId())) {
            result.setRemoved(true);
            return result;
        }
        String taskId = NotificationLinkSupport.extractTaskId(notification.getLink());
        if (taskId != null && !taskExists(taskId)) {
            userNotificationMapper.deleteByIdForUser(id, userId);
            result.setRemoved(true);
            result.setTaskDeleted(true);
            return result;
        }
        userNotificationMapper.markRead(id, userId);
        result.setRead(true);
        return result;
    }

    public int markAllRead() {
        String userId = SecurityUtils.getCurrentUserId();
        return userNotificationMapper.markAllRead(userId);
    }

    public int clearAll() {
        String userId = SecurityUtils.getCurrentUserId();
        return userNotificationMapper.deleteAllForUser(userId);
    }

    public void deleteByTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        userNotificationMapper.deleteByTaskIdInLink(taskId.trim());
    }

    /**
     * Per-user aggregated reminder: delete all unread notifications for the same rule,
     * then insert a fresh one. Returns previous Feishu message id if any.
     */
    public SiteNotificationReplaceResult replaceSiteNotification(String userId,
                                                                 String ruleId,
                                                                 String periodBucket,
                                                                 String title,
                                                                 String body,
                                                                 String link,
                                                                 String contentVarsJson) {
        String bucket = periodBucket != null && !periodBucket.trim().isEmpty()
                ? periodBucket.trim()
                : ReminderSupport.AGGREGATE_PERIOD_BUCKET;
        UserNotification existing = userNotificationMapper.selectUnreadByUserAndRule(userId, ruleId, bucket);
        String previousFeishuMessageId = existing != null ? existing.getFeishuMessageId() : null;
        userNotificationMapper.deleteUnreadByUserAndRule(userId, ruleId, bucket);
        UserNotification n = new UserNotification();
        String notificationId = IdGenerator.generateId();
        n.setId(notificationId);
        n.setUserId(userId);
        n.setRuleId(ruleId);
        n.setPeriodBucket(bucket);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setContentVarsJson(contentVarsJson);
        userNotificationMapper.insertNotification(n);
        return new SiteNotificationReplaceResult(notificationId, previousFeishuMessageId);
    }

    public void updateFeishuMessageId(String notificationId, String userId, String feishuMessageId) {
        if (notificationId == null || userId == null || feishuMessageId == null || feishuMessageId.trim().isEmpty()) {
            return;
        }
        userNotificationMapper.updateFeishuMessageId(notificationId, userId, feishuMessageId.trim());
    }

    private void pruneStaleTaskNotifications(String userId) {
        List<UserNotification> recent = userNotificationMapper.selectRecentByUser(userId, STALE_PRUNE_LIMIT);
        for (UserNotification notification : recent) {
            String taskId = NotificationLinkSupport.extractTaskId(notification.getLink());
            if (taskId == null) {
                continue;
            }
            if (!taskExists(taskId)) {
                userNotificationMapper.deleteByIdForUser(notification.getId(), userId);
            }
        }
    }

    private boolean taskExists(String taskId) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        return task != null;
    }

    private UserNotificationDTO toDto(UserNotification n, String locale) {
        UserNotificationDTO dto = new UserNotificationDTO();
        dto.setId(n.getId());
        dto.setRuleId(n.getRuleId());
        dto.setTitle(n.getTitle());
        dto.setBody(n.getBody());
        dto.setLink(n.getLink());
        dto.setRead(n.getReadAt() != null);
        dto.setReadAt(n.getReadAt());
        dto.setCreatedAt(n.getCreatedAt());
        NotificationLocalizationService.UserNotificationDTOHolder holder =
                new NotificationLocalizationService.UserNotificationDTOHolder(dto.getTitle(), dto.getBody());
        notificationLocalizationService.applyLocale(holder, n, locale);
        dto.setTitle(holder.getTitle());
        dto.setBody(holder.getBody());
        return dto;
    }
}
