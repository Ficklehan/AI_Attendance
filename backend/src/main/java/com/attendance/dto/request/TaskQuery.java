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
    /** 考勤记录导出：是否在 Excel 中嵌入考勤图片缩略图（默认 false，仅导出原图链接） */
    private Boolean includeThumbnails;
    /** 导出表头语言，与 PC 端 locale 一致，如 zh-CN、en-US */
    private String locale;

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

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

    public Boolean getIncludeThumbnails() {
        return includeThumbnails;
    }

    public void setIncludeThumbnails(Boolean includeThumbnails) {
        this.includeThumbnails = includeThumbnails;
    }
}