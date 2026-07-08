package com.attendance.dto.request;

/**
 * 员工列表 / 周考勤异步导出查询参数（与员工管理页筛选项对齐）。
 */
public class EmployeeExportQuery {

    /** 逗号分隔国家/地区键，对应 region_code */
    private String regionCodes;
    /** 单个地区键（regionCodes 为空时回退使用） */
    private String regionCode;
    /** 姓名/工号/中介关键字 */
    private String keyword;
    /** 周考勤：ISO 周，如 2026-W25；员工列表导出忽略 */
    private String isoWeek;
    /** 导出表头语言，与 PC 端 locale 一致，如 zh-CN、en-US */
    private String locale;

    public String getRegionCodes() {
        return regionCodes;
    }

    public void setRegionCodes(String regionCodes) {
        this.regionCodes = regionCodes;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getIsoWeek() {
        return isoWeek;
    }

    public void setIsoWeek(String isoWeek) {
        this.isoWeek = isoWeek;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
