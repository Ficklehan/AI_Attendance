package com.attendance.dto.response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoleDataScopeDTO {

    private String role;
    private String scopeType;
    private boolean editable;
    private Map<String, List<String>> rules = new LinkedHashMap<>();

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public Map<String, List<String>> getRules() {
        return rules;
    }

    public void setRules(Map<String, List<String>> rules) {
        this.rules = rules;
    }
}
