package com.attendance.dto.response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色功能权限：全局默认 + 按工作国家覆盖。
 */
public class RolePermissionsBundleDTO {

    private Map<String, Map<String, Boolean>> roles = new LinkedHashMap<>();
    /** role -> countryCode -> permissionKey -> enabled */
    private Map<String, Map<String, Map<String, Boolean>>> byCountry = new LinkedHashMap<>();

    public Map<String, Map<String, Boolean>> getRoles() {
        return roles;
    }

    public void setRoles(Map<String, Map<String, Boolean>> roles) {
        this.roles = roles != null ? roles : new LinkedHashMap<>();
    }

    public Map<String, Map<String, Map<String, Boolean>>> getByCountry() {
        return byCountry;
    }

    public void setByCountry(Map<String, Map<String, Map<String, Boolean>>> byCountry) {
        this.byCountry = byCountry != null ? byCountry : new LinkedHashMap<>();
    }
}
