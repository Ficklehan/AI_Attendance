package com.attendance.dto.response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeeklyAttendanceDTO {

    private String isoWeek;
    private String startDate;
    private String endDate;
    private List<String> days = new ArrayList<>();
    private List<WeeklyEmployeeRow> rows = new ArrayList<>();

    public String getIsoWeek() {
        return isoWeek;
    }

    public void setIsoWeek(String isoWeek) {
        this.isoWeek = isoWeek;
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

    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days != null ? days : new ArrayList<>();
    }

    public List<WeeklyEmployeeRow> getRows() {
        return rows;
    }

    public void setRows(List<WeeklyEmployeeRow> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public static class WeeklyEmployeeRow {
        private Long employeeId;
        private String empNo;
        private String displayName;
        private String regionCode;
        private String agencyKey;
        private Map<String, WeeklyCell> cells = new LinkedHashMap<>();

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public String getEmpNo() {
            return empNo;
        }

        public void setEmpNo(String empNo) {
            this.empNo = empNo;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRegionCode() {
            return regionCode;
        }

        public void setRegionCode(String regionCode) {
            this.regionCode = regionCode;
        }

        public String getAgencyKey() {
            return agencyKey;
        }

        public void setAgencyKey(String agencyKey) {
            this.agencyKey = agencyKey;
        }

        public Map<String, WeeklyCell> getCells() {
            return cells;
        }

        public void setCells(Map<String, WeeklyCell> cells) {
            this.cells = cells != null ? cells : new LinkedHashMap<>();
        }
    }

    public static class WeeklyCell {
        private boolean present;
        private Double workHours;

        public boolean isPresent() {
            return present;
        }

        public void setPresent(boolean present) {
            this.present = present;
        }

        public Double getWorkHours() {
            return workHours;
        }

        public void setWorkHours(Double workHours) {
            this.workHours = workHours;
        }
    }
}
