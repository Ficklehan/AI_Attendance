package com.attendance.dto.request;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

public class AdminUserUpdateRequest {

    @Email
    private String email;

    @Size(min = 6, max = 64)
    private String password;

    private String role;
    private String realName;
    private String employeeId;
    private String workingCountry;
    private String status;
    /** Bind Feishu open_id so mini-program login maps to this PC user; empty string clears. */
    private String feishuUserId;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFeishuUserId() {
        return feishuUserId;
    }

    public void setFeishuUserId(String feishuUserId) {
        this.feishuUserId = feishuUserId;
    }
}
