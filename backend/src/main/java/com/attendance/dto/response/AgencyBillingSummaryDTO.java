package com.attendance.dto.response;

import java.util.ArrayList;
import java.util.List;

public class AgencyBillingSummaryDTO {

    private String startDate;
    private String endDate;
    private List<AgencyBlock> blocks = new ArrayList<>();

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

    public List<AgencyBlock> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<AgencyBlock> blocks) {
        this.blocks = blocks != null ? blocks : new ArrayList<>();
    }

    public static class AgencyBlock {
        private String agencyKey;
        private String agencyLabel;
        private String warehouseKey;
        private String warehouseLabel;
        private String countryKey;
        private String countryLabel;
        private int headcount;
        private int attendanceDays;
        private double totalHours;

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

        public int getHeadcount() {
            return headcount;
        }

        public void setHeadcount(int headcount) {
            this.headcount = headcount;
        }

        public int getAttendanceDays() {
            return attendanceDays;
        }

        public void setAttendanceDays(int attendanceDays) {
            this.attendanceDays = attendanceDays;
        }

        public double getTotalHours() {
            return totalHours;
        }

        public void setTotalHours(double totalHours) {
            this.totalHours = totalHours;
        }
    }
}
