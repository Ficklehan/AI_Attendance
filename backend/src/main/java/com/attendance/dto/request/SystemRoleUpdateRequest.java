package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class SystemRoleUpdateRequest {

    @NotBlank
    @Size(min = 1, max = 64)
    private String roleName;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
