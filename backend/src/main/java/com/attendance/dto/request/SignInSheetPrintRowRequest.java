package com.attendance.dto.request;

import javax.validation.constraints.Size;

public class SignInSheetPrintRowRequest {

    private Integer seqNo;

    @Size(max = 128)
    private String personName;

    @Size(max = 256)
    private String agencyName;

    @Size(max = 128)
    private String shiftName;

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }
}
