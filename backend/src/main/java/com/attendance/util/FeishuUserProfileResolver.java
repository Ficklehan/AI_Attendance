package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 从飞书 authen/user_info 解析可展示的用户名与邮箱。
 * 飞书在未开通邮箱权限或仅有占位邮箱时，email 可能为 open_id@feishu.cn / @larksuite.com。
 */
public final class FeishuUserProfileResolver {

    private static final Pattern USERNAME_SAFE = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,47}$");
    private static final String INTERNAL_EMAIL_DOMAIN = "feishu.internal";

    private FeishuUserProfileResolver() {
    }

    public static final class Profile {
        private final String realName;
        private final String email;
        private final String username;
        private final String employeeId;

        public Profile(String realName, String email, String username, String employeeId) {
            this.realName = realName;
            this.email = email;
            this.username = username;
            this.employeeId = employeeId;
        }

        public String getRealName() {
            return realName;
        }

        /** 真实邮箱；无则 null（入库时使用内部占位邮箱） */
        public String getEmail() {
            return email;
        }

        public String getUsername() {
            return username;
        }

        public String getEmployeeId() {
            return employeeId;
        }
    }

    public static Profile resolve(JSONObject userInfo, String feishuUserId) {
        if (userInfo == null) {
            userInfo = new JSONObject();
        }
        String name = trimToNull(userInfo.getString("name"));
        String enName = trimToNull(userInfo.getString("en_name"));
        String enterpriseEmail = trimToNull(userInfo.getString("enterprise_email"));
        String email = trimToNull(userInfo.getString("email"));
        String employeeNo = trimToNull(userInfo.getString("employee_no"));

        String realName = name != null ? name : enName;
        String resolvedEmail = resolveEmail(enterpriseEmail, email, feishuUserId);
        String username = resolveUsername(enName, realName, employeeNo, feishuUserId);
        return new Profile(realName, resolvedEmail, username, employeeNo);
    }

    public static boolean isPlaceholderEmail(String email, String feishuUserId) {
        if (email == null || email.trim().isEmpty()) {
            return true;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("@" + INTERNAL_EMAIL_DOMAIN) || trimmed.endsWith("@feishu.user")
                || trimmed.endsWith("@local.internal")) {
            return true;
        }
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            return true;
        }
        String local = trimmed.substring(0, at);
        if (local.startsWith("ou_") || local.startsWith("on_")) {
            return true;
        }
        if (feishuUserId != null && local.equals(feishuUserId.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    public static String displayEmail(String email, String feishuUserId) {
        return isPlaceholderEmail(email, feishuUserId) ? null : email;
    }

    public static String buildInternalEmail(String feishuUserId) {
        String id = feishuUserId != null ? feishuUserId.trim().toLowerCase(Locale.ROOT) : "unknown";
        return id + "@" + INTERNAL_EMAIL_DOMAIN;
    }

    /** 管理员创建用户未填邮箱时的占位地址（对外不展示） */
    public static String buildLocalEmail(String username) {
        String base = username != null ? username.trim().toLowerCase(Locale.ROOT) : "user";
        base = base.replaceAll("[^a-z0-9._-]", ".")
                .replaceAll("^\\.+|\\.+$", "")
                .replaceAll("\\.{2,}", ".");
        if (base.isEmpty()) {
            base = "user";
        }
        return base + "@local.internal";
    }

    public static boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    public static boolean isPlaceholderUsername(String username, String feishuUserId) {
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        String trimmed = username.trim();
        if (trimmed.startsWith("ou_") || trimmed.startsWith("on_")) {
            return true;
        }
        if (feishuUserId != null && trimmed.equals(feishuUserId)) {
            return true;
        }
        if (trimmed.startsWith("feishu_") && feishuUserId != null) {
            String suffix = feishuUserId.replace("ou_", "").replace("on_", "");
            if (suffix.length() >= 6 && trimmed.contains(suffix.substring(0, Math.min(8, suffix.length())))) {
                return true;
            }
        }
        return false;
    }

    static String resolveEmail(String enterpriseEmail, String email, String feishuUserId) {
        if (enterpriseEmail != null && !isPlaceholderEmail(enterpriseEmail, feishuUserId)) {
            return enterpriseEmail;
        }
        if (email != null && !isPlaceholderEmail(email, feishuUserId)) {
            return email;
        }
        return null;
    }

    static String resolveUsername(String enName, String realName, String employeeNo, String feishuUserId) {
        String fromEn = slugifyUsername(enName);
        if (fromEn != null) {
            return fromEn;
        }
        String fromEmployee = slugifyUsername(employeeNo);
        if (fromEmployee != null) {
            return fromEmployee;
        }
        String fromName = slugifyUsername(realName);
        if (fromName != null) {
            return fromName;
        }
        return defaultUsername(feishuUserId);
    }

    static String slugifyUsername(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String slug = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "")
                .replaceAll("\\.{2,}", ".");
        if (slug.length() < 2 || !USERNAME_SAFE.matcher(slug).matches()) {
            return null;
        }
        return slug;
    }

    static String defaultUsername(String feishuUserId) {
        if (feishuUserId == null || feishuUserId.trim().isEmpty()) {
            return "feishu_user";
        }
        String compact = feishuUserId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (compact.length() > 16) {
            compact = compact.substring(compact.length() - 16);
        }
        return "feishu_" + compact;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
