package com.attendance.dto.response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgencyBillingDetailDTO {

    private String startDate;
    private String endDate;
    private String agencyKey;
    private String agencyLabel;
    private String warehouseKey;
    private String warehouseLabel;
    private String countryKey;
    private String countryLabel;
    private List<String> days = new ArrayList<>();
    private List<EmployeeRow> rows = new ArrayList<>();
    private int totalHeadcount;
    private double totalHours;

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

    public String getAgencyKey() {
        return agencyKey;
    }

    public void setAgencyKey(String agencyKey) {
        this.agencyKey = agencyKey;
    }

    public String getAgencyLabel() {
        return agencyLabel;
    }

    public void setAgencyLabel(String agencyLabel) {
        this.agencyLabel = agencyLabel;
    }

    public String getWarehouseKey() {
        return warehouseKey;
    }

    public void setWarehouseKey(String warehouseKey) {
        this.warehouseKey = warehouseKey;
    }

    public String getWarehouseLabel() {
        return warehouseLabel;
    }

    public void setWarehouseLabel(String warehouseLabel) {
        this.warehouseLabel = warehouseLabel;
    }

    public String getCountryKey() {
        return countryKey;
    }

    public void setCountryKey(String countryKey) {
        this.countryKey = countryKey;
    }

    public String getCountryLabel() {
        return countryLabel;
    }

    public void setCountryLabel(String countryLabel) {
        this.countryLabel = countryLabel;
    }

    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days != null ? days : new ArrayList<>();
    }

    public List<EmployeeRow> getRows() {
        return rows;
    }

    public void setRows(List<EmployeeRow> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public int getTotalHeadcount() {
        return totalHeadcount;
    }

    public void setTotalHeadcount(int totalHeadcount) {
        this.totalHeadcount = totalHeadcount;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    public static class EmployeeRow {
        private String employeeKey;
        private String empNo;
        private String displayName;
        private Map<String, DayCell> cells = new LinkedHashMap<>();
        private double totalHours;
        private List<LineItem> lines = new ArrayList<>();

        public String getEmployeeKey() {
            return employeeKey;
        }

        public void setEmployeeKey(String employeeKey) {
            this.employeeKey = employeeKey;
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

        public Map<String, DayCell> getCells() {
            return cells;
        }

        public void setCells(Map<String, DayCell> cells) {
            this.cells = cells != null ? cells : new LinkedHashMap<>();
        }

        public double getTotalHours() {
            return totalHours;
        }

        public void setTotalHours(double totalHours) {
            this.totalHours = totalHours;
        }

        public List<LineItem> getLines() {
            return lines;
        }

        public void setLines(List<LineItem> lines) {
            this.lines = lines != null ? lines : new ArrayList<>();
        }
    }

    public static class DayCell {
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

    public static class LineItem {
        private String workDate;
        private String shift;
        private String arrival;
        private String departure;
        private String pauseMinutes;
        private Double workHours;
        private String observations;

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

        public Double getWorkHours() {
            return workHours;
        }

        public void setWorkHours(Double workHours) {
            this.workHours = workHours;
        }

        public String getObservations() {
            return observations;
        }

        public void setObservations(String observations) {
            this.observations = observations;
        }
    }
}
