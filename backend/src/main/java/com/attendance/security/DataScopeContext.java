package com.attendance.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析后的数据访问范围，供 Mapper 过滤 tasks / task_records。
 */
public class DataScopeContext {

    public static final String SELF_TOKEN = "__self__";

    private boolean allUsers;
    private List<String> ownerUserIds = Collections.emptyList();
    private List<String> countries = Collections.emptyList();
    /** 展开后的国家匹配 token（含 CHINA、ITALIA 等历史别名），供 SQL IN 过滤 */
    private List<String> countryMatchTokens = Collections.emptyList();
    private List<String> warehouses = Collections.emptyList();
    private List<String> agencies = Collections.emptyList();
    private List<String> workRegions = Collections.emptyList();
    private boolean recordDimensionFilter;
    private boolean employeeDimensionFilter;
    /** 当前登录用户 ID，用于识别中任务在尚无记录时的列表可见性 */
    private String viewerUserId;

    public static DataScopeContext allUsers() {
        DataScopeContext ctx = new DataScopeContext();
        ctx.allUsers = true;
        return ctx;
    }

    public boolean isAllUsers() {
        return allUsers;
    }

    public void setAllUsers(boolean allUsers) {
        this.allUsers = allUsers;
    }

    public List<String> getOwnerUserIds() {
        return ownerUserIds;
    }

    public void setOwnerUserIds(List<String> ownerUserIds) {
        this.ownerUserIds = ownerUserIds != null ? new ArrayList<>(ownerUserIds) : Collections.emptyList();
    }

    public List<String> getCountries() {
        return countries;
    }

    public void setCountries(List<String> countries) {
        this.countries = countries != null ? new ArrayList<>(countries) : Collections.emptyList();
        refreshRecordDimensionFlag();
    }

    public List<String> getCountryMatchTokens() {
        return countryMatchTokens;
    }

    public void setCountryMatchTokens(List<String> countryMatchTokens) {
        this.countryMatchTokens = countryMatchTokens != null
                ? new ArrayList<>(countryMatchTokens) : Collections.emptyList();
    }

    public List<String> getWarehouses() {
        return warehouses;
    }

    public void setWarehouses(List<String> warehouses) {
        this.warehouses = warehouses != null ? new ArrayList<>(warehouses) : Collections.emptyList();
        refreshRecordDimensionFlag();
    }

    public List<String> getAgencies() {
        return agencies;
    }

    public void setAgencies(List<String> agencies) {
        this.agencies = agencies != null ? new ArrayList<>(agencies) : Collections.emptyList();
        refreshRecordDimensionFlag();
    }

    public List<String> getWorkRegions() {
        return workRegions;
    }

    public void setWorkRegions(List<String> workRegions) {
        this.workRegions = workRegions != null ? new ArrayList<>(workRegions) : Collections.emptyList();
        refreshEmployeeDimensionFlag();
    }

    public boolean isRecordDimensionFilter() {
        return recordDimensionFilter;
    }

    public boolean isEmployeeDimensionFilter() {
        return employeeDimensionFilter;
    }

    public boolean hasOwnerUserFilter() {
        return !allUsers && ownerUserIds != null && !ownerUserIds.isEmpty();
    }

    public boolean isRestricted() {
        return !allUsers;
    }

    public String getViewerUserId() {
        return viewerUserId;
    }

    public void setViewerUserId(String viewerUserId) {
        this.viewerUserId = viewerUserId;
    }

    private void refreshRecordDimensionFlag() {
        recordDimensionFilter = (countries != null && !countries.isEmpty())
                || (warehouses != null && !warehouses.isEmpty())
                || (agencies != null && !agencies.isEmpty());
    }

    private void refreshEmployeeDimensionFlag() {
        employeeDimensionFilter = workRegions != null && !workRegions.isEmpty();
    }
}
