package com.attendance.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SignInSheetPrintJob {
    private Long id;
    private String countryCode;
    private LocalDate workDate;
    private String warehouse;
    private String sheetLocale;
    private Integer pageCount;
    private String printedBy;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }

    public String getSheetLocale() {
        return sheetLocale;
    }

    public void setSheetLocale(String sheetLocale) {
        this.sheetLocale = sheetLocale;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getPrintedBy() {
        return printedBy;
    }

    public void setPrintedBy(String printedBy) {
        this.printedBy = printedBy;
    }

    public LocalDateTime getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(LocalDateTime printedAt) {
        this.printedAt = printedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
