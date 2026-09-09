package com.attendance.dto.request;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class SignInSheetPrintJobRequest {

    @NotBlank
    @Size(max = 32)
    private String countryCode;

    @NotBlank
    private String workDate;

    @NotBlank
    @Size(max = 128)
    private String warehouse;

    @NotBlank
    @Size(max = 16)
    private String sheetLocale;

    @NotNull
    @Min(1)
    @Max(200)
    private Integer pageCount;

    @Valid
    @Size(max = 2000)
    private List<SignInSheetPrintRowRequest> rows = new ArrayList<>();

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getWorkDate() {
        return workDate;
    }

    public void setWorkDate(String workDate) {
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

    public List<SignInSheetPrintRowRequest> getRows() {
        return rows;
    }

    public void setRows(List<SignInSheetPrintRowRequest> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }
}
