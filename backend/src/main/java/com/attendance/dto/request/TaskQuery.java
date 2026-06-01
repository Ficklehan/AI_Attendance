package com.attendance.dto.request;

public class TaskQuery {
    private String status;
    private String keyword;
    private String searchField;
    private String filters;
    private Long current = 1L;
    private Long size = 20L;
    /** Server-only: list/export scope user id; null means all users (admin). */
    private String listScopeUserId;
    /** Server-only: when true, list/export includes all users (admin). */
    private Boolean allUsersScope;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getSearchField() {
        return searchField;
    }

    public void setSearchField(String searchField) {
        this.searchField = searchField;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }

    public Long getCurrent() {
        return current;
    }

    public void setCurrent(Long current) {
        this.current = current;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getOffset() {
        return (current - 1) * size;
    }

    public String getListScopeUserId() {
        return listScopeUserId;
    }

    public void setListScopeUserId(String listScopeUserId) {
        this.listScopeUserId = listScopeUserId;
    }

    public Boolean getAllUsersScope() {
        return allUsersScope;
    }

    public void setAllUsersScope(Boolean allUsersScope) {
        this.allUsersScope = allUsersScope;
    }
}