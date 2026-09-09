package com.attendance.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SignInSheetPrintDayDTO {
    private String countryCode;
    private LocalDate workDate;
    private Integer personCount;
    private Integer jobCount;
    private List<String> warehouses = new ArrayList<>();

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public Integer getPersonCount() {
        return personCount;
    }

    public void setPersonCount(Integer personCount) {
        this.personCount = personCount;
    }

    public Integer getJobCount() {
        return jobCount;
    }

    public void setJobCount(Integer jobCount) {
        this.jobCount = jobCount;
    }

    public List<String> getWarehouses() {
        return warehouses;
    }

    public void setWarehouses(List<String> warehouses) {
        this.warehouses = warehouses != null ? warehouses : new ArrayList<>();
    }

    /** MyBatis maps GROUP_CONCAT alias warehousesCsv into this setter. */
    @JsonIgnore
    public void setWarehousesCsv(String warehousesCsv) {
        if (warehousesCsv == null || warehousesCsv.trim().isEmpty()) {
            this.warehouses = new ArrayList<>();
            return;
        }
        this.warehouses = Arrays.stream(warehousesCsv.split("\\|\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
