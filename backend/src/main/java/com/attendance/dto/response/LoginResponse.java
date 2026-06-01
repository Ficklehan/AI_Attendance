package com.attendance.dto.response;

public class LoginResponse {
    private String token;
    private UserInfo userInfo;

    public LoginResponse() {
    }

    public LoginResponse(String token, UserInfo userInfo) {
        this.token = token;
        this.userInfo = userInfo;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String role;
        private String realName;
        private java.util.Map<String, Boolean> permissions;

        public UserInfo() {
        }

        public UserInfo(String id, String username, String email, String role, String realName) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
            this.realName = realName;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public java.util.Map<String, Boolean> getPermissions() {
            return permissions;
        }

        public void setPermissions(java.util.Map<String, Boolean> permissions) {
            this.permissions = permissions;
        }
    }
}