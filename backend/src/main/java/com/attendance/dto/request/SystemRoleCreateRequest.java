package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class SystemRoleCreateRequest {

    @NotBlank
    @Size(min = 2, max = 32)
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "roleKey must be lowercase letters, digits or underscore")
    private String roleKey;

    @NotBlank
    @Size(min = 1, max = 64)
    private String roleName;

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
