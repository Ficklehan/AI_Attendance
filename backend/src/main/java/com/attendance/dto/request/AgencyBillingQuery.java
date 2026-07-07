package com.attendance.dto.request;

/**
 * 中介考勤账单查询（数据来源：考勤记录 task_records，仅已确认且未删除）。
 */
public class AgencyBillingQuery {

    private String startDate;
    private String endDate;
    /** 逗号分隔国家/地区键，对应 country_key */
    private String regionCodes;
    /** 逗号分隔仓库键 */
    private String warehouseKeys;
    /** 详情/导出：指定中介键 */
    private String agencyKey;
    /** 详情/导出：指定仓库键 */
    private String warehouseKey;
    /** 详情/导出：指定国家键 */
    private String countryKey;
    /** 导出表头语言，与 PC 端 locale 一致，如 zh-CN、en-US */
    private String locale;

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getRegionCodes() {
        return regionCodes;
    }

    public void setRegionCodes(String regionCodes) {
        this.regionCodes = regionCodes;
    }

    public String getWarehouseKeys() {
        return warehouseKeys;
    }

    public void setWarehouseKeys(String warehouseKeys) {
        this.warehouseKeys = warehouseKeys;
    }

    public String getAgencyKey() {
        return agencyKey;
    }

    public void setAgencyKey(String agencyKey) {
        this.agencyKey = agencyKey;
    }

    public String getWarehouseKey() {
        return warehouseKey;
    }

    public void setWarehouseKey(String warehouseKey) {
        this.warehouseKey = warehouseKey;
    }

    public String getCountryKey() {
        return countryKey;
    }

    public void setCountryKey(String countryKey) {
        this.countryKey = countryKey;
    }
}
