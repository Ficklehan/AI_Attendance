package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.response.NotificationReadResultDTO;
import com.attendance.dto.response.UserNotificationDTO;
import com.attendance.service.UserNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Validated
public class NotificationController {

    @Autowired
    private UserNotificationService userNotificationService;

    @GetMapping
    public Result<PageResult<UserNotificationDTO>> list(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String locale,
            @RequestHeader(value = "X-Locale", required = false) String localeHeader) {
        String resolvedLocale = locale != null && !locale.trim().isEmpty() ? locale.trim() : localeHeader;
        return Result.success(userNotificationService.list(current, size, resolvedLocale));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        Map<String, Long> body = new HashMap<>();
        body.put("count", userNotificationService.unreadCount());
        return Result.success(body);
    }

    @PostMapping("/{id}/read")
    public Result<NotificationReadResultDTO> markRead(@PathVariable String id) {
        return Result.success(userNotificationService.markRead(id));
    }

    @PostMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        Map<String, Integer> body = new HashMap<>();
        body.put("updated", userNotificationService.markAllRead());
        return Result.success(body);
    }

    @PostMapping("/clear-all")
    public Result<Map<String, Integer>> clearAll() {
        Map<String, Integer> body = new HashMap<>();
        body.put("deleted", userNotificationService.clearAll());
        return Result.success(body);
    }
}
