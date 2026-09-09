package com.attendance.dto.response;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SignInSheetPrintDayDetailDTO {
    private String countryCode;
    private LocalDate workDate;
    private List<SignInSheetPrintJobDTO> jobs = new ArrayList<>();
    private List<SignInSheetPrintPersonDTO> persons = new ArrayList<>();

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

    public List<SignInSheetPrintJobDTO> getJobs() {
        return jobs;
    }

    public void setJobs(List<SignInSheetPrintJobDTO> jobs) {
        this.jobs = jobs != null ? jobs : new ArrayList<>();
    }

    public List<SignInSheetPrintPersonDTO> getPersons() {
        return persons;
    }

    public void setPersons(List<SignInSheetPrintPersonDTO> persons) {
        this.persons = persons != null ? persons : new ArrayList<>();
    }
}
