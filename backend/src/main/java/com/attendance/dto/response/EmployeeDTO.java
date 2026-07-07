package com.attendance.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeeDTO {

    private Long id;
    private String empNo;
    private String regionCode;
    private String agencyKey;
    private String matchName;
    private String displayName;
    private int status;
    private LocalDateTime firstCreatedAt;
    private LocalDate lastAttendanceDate;
    private LocalDateTime lastSeenAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
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

    public String getMatchName() {
        return matchName;
    }

    public void setMatchName(String matchName) {
        this.matchName = matchName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LocalDateTime getFirstCreatedAt() {
        return firstCreatedAt;
    }

    public void setFirstCreatedAt(LocalDateTime firstCreatedAt) {
        this.firstCreatedAt = firstCreatedAt;
    }

    public LocalDate getLastAttendanceDate() {
        return lastAttendanceDate;
    }

    public void setLastAttendanceDate(LocalDate lastAttendanceDate) {
        this.lastAttendanceDate = lastAttendanceDate;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
