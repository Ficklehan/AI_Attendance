package com.attendance.dto.internal;

import com.alibaba.fastjson.JSONObject;

/** 中介账单瘦行：不含图片等大字段，用于聚合与导出 */
public class AgencyBillingRow {

    private String agencyKey;
    private String agency;
    private String warehouseKey;
    private String warehouse;
    private String countryKey;
    private String country;
    private String employeeNo;
    private String empName;
    private String workDate;
    private String shift;
    private String arrival;
    private String departure;
    private String pauseMinutes;
    private String observations;
    private String smartMark;

    public JSONObject toReadabilityJson() {
        JSONObject record = new JSONObject();
        record.put("NO", employeeNo);
        record.put("NOM_PRENOM", empName);
        record.put("ARRIVEE", arrival);
        record.put("DEPAR", departure);
        record.put("SmartMark", smartMark);
        record.put("Mark", smartMark);
        return record;
    }

    public JSONObject toHoursJson() {
        JSONObject record = new JSONObject();
        record.put("SmartMark", smartMark);
        record.put("ARRIVEE", arrival);
        record.put("DEPAR", departure);
        record.put("PAUSE", pauseMinutes);
        return record;
    }

    public String getAgencyKey() {
        return agencyKey;
    }

    public void setAgencyKey(String agencyKey) {
        this.agencyKey = agencyKey;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public String getWarehouseKey() {
        return warehouseKey;
    }

    public void setWarehouseKey(String warehouseKey) {
        this.warehouseKey = warehouseKey;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }

    public String getCountryKey() {
        return countryKey;
    }

    public void setCountryKey(String countryKey) {
        this.countryKey = countryKey;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getWorkDate() {
        return workDate;
    }

    public void setWorkDate(String workDate) {
        this.workDate = workDate;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getArrival() {
        return arrival;
    }

    public void setArrival(String arrival) {
        this.arrival = arrival;
    }

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getPauseMinutes() {
        return pauseMinutes;
    }

    public void setPauseMinutes(String pauseMinutes) {
        this.pauseMinutes = pauseMinutes;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public String getSmartMark() {
        return smartMark;
    }

    public void setSmartMark(String smartMark) {
        this.smartMark = smartMark;
    }
}
