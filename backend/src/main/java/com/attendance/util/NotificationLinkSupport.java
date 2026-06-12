package com.attendance.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotificationLinkSupport {

    private static final Pattern TASK_LINK = Pattern.compile("/tasks/([^/?#]+)");

    private NotificationLinkSupport() {
    }

    public static String extractTaskId(String link) {
        if (link == null || link.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = TASK_LINK.matcher(link.trim());
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
