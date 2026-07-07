package com.attendance.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

public class RolePermissionsUpdateRequest {

    private Map<String, Map<String, Boolean>> roles = new LinkedHashMap<>();
    private Map<String, Map<String, Map<String, Boolean>>> byCountry = new LinkedHashMap<>();

    public Map<String, Map<String, Boolean>> getRoles() {
        return roles;
    }

    public void setRoles(Map<String, Map<String, Boolean>> roles) {
        this.roles = roles;
    }

    public Map<String, Map<String, Map<String, Boolean>>> getByCountry() {
        return byCountry;
    }

    public void setByCountry(Map<String, Map<String, Map<String, Boolean>>> byCountry) {
        this.byCountry = byCountry;
    }
}
