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
        // 打印归档不做数据范围权限控制，仅要求已登录

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
        return signInSheetPrintMapper.selectDays(scope, country, fromDate, toDate);
    }

    public SignInSheetPrintDayDetailDTO getDayDetail(String countryCode, String workDate) {
        if (!StringUtils.hasText(countryCode) || !StringUtils.hasText(workDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ErrorKeys.VALIDATION_FAILED);
        }
        String country = countryCode.trim();
        LocalDate date = parseDate(workDate);

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
