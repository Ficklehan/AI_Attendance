package com.attendance.dto.request;

import java.util.List;
import java.util.Map;

public class RoleDataScopeUpdateRequest {

    private String scopeType;
    private Map<String, List<String>> rules;

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Map<String, List<String>> getRules() {
        return rules;
    }

    public void setRules(Map<String, List<String>> rules) {
        this.rules = rules;
    }
}
