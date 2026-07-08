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
    /** 考勤记录导出：嵌入的图片是否使用原图全分辨率（默认 false=嵌入右尺寸预览；原图始终由链接列保留） */
    private Boolean embedFullResolution;
    /** 导出表头语言，与 PC 端 locale 一致，如 zh-CN、en-US */
    private String locale;
    /** 考勤记录导出：原图超链接的根地址（含 context-path），创建导出任务时按当前请求域名派生；空则回退配置 export.public-base-url */
    private String imageBaseUrl;

    public String getImageBaseUrl() {
        return imageBaseUrl;
    }

    public void setImageBaseUrl(String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;
    }

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

    public Boolean getEmbedFullResolution() {
        return embedFullResolution;
    }

    public void setEmbedFullResolution(Boolean embedFullResolution) {
        this.embedFullResolution = embedFullResolution;
    }
}