package com.attendance.mapper;

import com.attendance.dto.response.SignInSheetPrintDayDTO;
import com.attendance.dto.response.SignInSheetPrintJobDTO;
import com.attendance.dto.response.SignInSheetPrintPersonDTO;
import com.attendance.entity.SignInSheetPrintJob;
import com.attendance.entity.SignInSheetPrintRow;
import com.attendance.security.DataScopeContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SignInSheetPrintMapper {

    int insertJob(SignInSheetPrintJob job);

    int insertRow(SignInSheetPrintRow row);

    List<SignInSheetPrintDayDTO> selectDays(
            @Param("scope") DataScopeContext scope,
            @Param("countryCode") String countryCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    List<SignInSheetPrintJobDTO> selectJobsForDay(
            @Param("scope") DataScopeContext scope,
            @Param("countryCode") String countryCode,
            @Param("workDate") LocalDate workDate);

    List<SignInSheetPrintPersonDTO> selectPersonsForDay(
            @Param("scope") DataScopeContext scope,
            @Param("countryCode") String countryCode,
            @Param("workDate") LocalDate workDate);
}
