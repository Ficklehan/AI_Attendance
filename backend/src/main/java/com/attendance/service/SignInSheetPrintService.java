package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.request.SignInSheetPrintJobRequest;
import com.attendance.dto.request.SignInSheetPrintRowRequest;
import com.attendance.dto.response.SignInSheetPrintDayDTO;
import com.attendance.dto.response.SignInSheetPrintDayDetailDTO;
import com.attendance.dto.response.SignInSheetPrintJobDTO;
import com.attendance.dto.response.SignInSheetPrintPersonDTO;
import com.attendance.entity.SignInSheetPrintJob;
import com.attendance.entity.SignInSheetPrintRow;
import com.attendance.mapper.SignInSheetPrintMapper;
import com.attendance.security.DataScopeContext;
import com.attendance.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SignInSheetPrintService {

    @Autowired
    private SignInSheetPrintMapper signInSheetPrintMapper;

    @Autowired
    private DataScopeService dataScopeService;

    @Transactional
    public SignInSheetPrintJobDTO createJob(SignInSheetPrintJobRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }

        String countryCode = trimToEmpty(request.getCountryCode());
        String warehouse = trimToEmpty(request.getWarehouse());
        String sheetLocale = trimToEmpty(request.getSheetLocale());
        if (!StringUtils.hasText(countryCode) || "default".equalsIgnoreCase(countryCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }
        if (!StringUtils.hasText(warehouse)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }
        if (!StringUtils.hasText(sheetLocale)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }

        LocalDate workDate = parseDate(request.getWorkDate());
        // 归档是打印审计（printed_by），不是国家数据权限门禁；登录用户即可创建。
        // 历史查询仍按国家/工作地区或「本人 printed_by」过滤。

        List<SignInSheetPrintRowRequest> sourceRows = request.getRows() != null
                ? request.getRows() : new ArrayList<SignInSheetPrintRowRequest>();
        List<SignInSheetPrintRowRequest> rows = new ArrayList<>();
        for (SignInSheetPrintRowRequest row : sourceRows) {
            if (row == null) {
                continue;
            }
            if (isBlankRow(row)) {
                continue;
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }

        SignInSheetPrintJob job = new SignInSheetPrintJob();
        job.setCountryCode(countryCode);
        job.setWorkDate(workDate);
        job.setWarehouse(warehouse);
        job.setSheetLocale(sheetLocale);
        job.setPageCount(request.getPageCount() != null ? request.getPageCount() : 1);
        job.setPrintedBy(userId);
        job.setPrintedAt(LocalDateTime.now());
        signInSheetPrintMapper.insertJob(job);

        int index = 0;
        for (SignInSheetPrintRowRequest src : rows) {
            SignInSheetPrintRow row = new SignInSheetPrintRow();
            row.setJobId(job.getId());
            row.setRowIndex(index++);
            row.setSeqNo(src.getSeqNo());
            row.setPersonName(trimToEmpty(src.getPersonName()));
            row.setAgencyName(trimToEmpty(src.getAgencyName()));
            row.setShiftName(trimToEmpty(src.getShiftName()));
            signInSheetPrintMapper.insertRow(row);
        }

        SignInSheetPrintJobDTO dto = new SignInSheetPrintJobDTO();
        dto.setId(job.getId());
        dto.setCountryCode(job.getCountryCode());
        dto.setWorkDate(job.getWorkDate());
        dto.setWarehouse(job.getWarehouse());
        dto.setSheetLocale(job.getSheetLocale());
        dto.setPageCount(job.getPageCount());
        dto.setPrintedBy(job.getPrintedBy());
        dto.setPrintedAt(job.getPrintedAt());
        dto.setPersonCount(rows.size());
        return dto;
    }

    public List<SignInSheetPrintDayDTO> listDays(String countryCode, String from, String to) {
        DataScopeContext scope = dataScopeService.resolveForCurrentUser();
        LocalDate fromDate = parseOptionalDate(from);
        LocalDate toDate = parseOptionalDate(to);
        String country = StringUtils.hasText(countryCode) ? countryCode.trim() : null;
        if (country != null) {
            assertCountryAllowed(country);
        }
        return signInSheetPrintMapper.selectDays(scope, country, fromDate, toDate);
    }

    public SignInSheetPrintDayDetailDTO getDayDetail(String countryCode, String workDate) {
        if (!StringUtils.hasText(countryCode) || !StringUtils.hasText(workDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }
        String country = countryCode.trim();
        LocalDate date = parseDate(workDate);
        assertCountryAllowed(country);

        DataScopeContext scope = dataScopeService.resolveForCurrentUser();
        List<SignInSheetPrintJobDTO> jobs = signInSheetPrintMapper.selectJobsForDay(scope, country, date);
        List<SignInSheetPrintPersonDTO> persons = signInSheetPrintMapper.selectPersonsForDay(scope, country, date);

        SignInSheetPrintDayDetailDTO detail = new SignInSheetPrintDayDetailDTO();
        detail.setCountryCode(country);
        detail.setWorkDate(date);
        detail.setJobs(jobs);
        detail.setPersons(persons);
        return detail;
    }

    /**
     * 历史读权限：有国家/工作地区范围时必须命中；仅本人范围不卡国家（SQL 按 printed_by 收口）。
     */
    private void assertCountryAllowed(String countryCode) {
        DataScopeContext scope = dataScopeService.resolveForCurrentUser();
        if (scope.isAllUsers()) {
            return;
        }
        if (!hasCountryOrRegionScope(scope)) {
            return;
        }
        List<String> tokens = scope.getCountryMatchTokens();
        if (tokens != null && !tokens.isEmpty()) {
            String upper = countryCode.trim().toUpperCase();
            for (String token : tokens) {
                if (token != null && upper.equals(token.trim().toUpperCase())) {
                    return;
                }
            }
        }
        List<String> regions = scope.getWorkRegions();
        if (regions != null) {
            for (String region : regions) {
                if (region != null && countryCode.trim().equalsIgnoreCase(region.trim())) {
                    return;
                }
            }
        }
        throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.ACCESS_DENIED);
    }

    private static boolean hasCountryOrRegionScope(DataScopeContext scope) {
        if (scope == null) {
            return false;
        }
        List<String> tokens = scope.getCountryMatchTokens();
        if (tokens != null && !tokens.isEmpty()) {
            return true;
        }
        List<String> regions = scope.getWorkRegions();
        return regions != null && !regions.isEmpty();
    }

    private static boolean isBlankRow(SignInSheetPrintRowRequest row) {
        boolean noSeq = row.getSeqNo() == null;
        boolean noName = !StringUtils.hasText(row.getPersonName());
        boolean noAgency = !StringUtils.hasText(row.getAgencyName());
        boolean noShift = !StringUtils.hasText(row.getShiftName());
        return noSeq && noName && noAgency && noShift;
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(trimToEmpty(raw));
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }
    }

    private static LocalDate parseOptionalDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return parseDate(raw);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
