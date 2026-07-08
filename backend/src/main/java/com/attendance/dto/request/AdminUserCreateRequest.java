package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AdminUserCreateRequest {

    @NotBlank
    @Size(min = 2, max = 64)
    private String username;

    private String email;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    private String role = "user";
    private java.util.List<String> roles;

    @NotBlank
    @Size(max = 128)
    private String realName;
    private String employeeId;
    private String workingCountry;

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

    public java.util.List<String> getRoles() {
        return roles;
    }

    public void setRoles(java.util.List<String> roles) {
        this.roles = roles;
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
}
