package com.attendance.entity;

import java.time.LocalDateTime;
import java.util.Map;

public class User {
    private String id;
    private String username;
    private String email;
    private String passwordHash;
    private String feishuUserId;
    private String role;
    private String realName;
    private String employeeId;
    /** 工作地区代码；null 或 default 表示使用系统默认 */
    private String workingCountry;
    /** 个人工作地区（仅 API 响应，非数据库字段） */
    private String personalWorkingCountry;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 当前用户有效权限（仅 API 响应，非数据库字段） */
    private Map<String, Boolean> permissions;

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Boolean> permissions) {
        this.permissions = permissions;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFeishuUserId() {
        return feishuUserId;
    }

    public void setFeishuUserId(String feishuUserId) {
        this.feishuUserId = feishuUserId;
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

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getWorkingCountry() {
        return workingCountry;
    }

    public void setWorkingCountry(String workingCountry) {
        this.workingCountry = workingCountry;
    }

    public String getPersonalWorkingCountry() {
        return personalWorkingCountry;
    }

    public void setPersonalWorkingCountry(String personalWorkingCountry) {
        this.personalWorkingCountry = personalWorkingCountry;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}