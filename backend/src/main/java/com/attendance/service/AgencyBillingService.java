package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.dto.internal.AgencyBillingBundle;
import com.attendance.dto.request.AgencyBillingQuery;
import com.attendance.dto.response.AgencyBillingDetailDTO;
import com.attendance.dto.response.AgencyBillingSummaryDTO;
import com.attendance.security.DataScopeContext;
import com.attendance.util.RecordJsonSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgencyBillingService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_RANGE_DAYS = 62;

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private AgencyBillingCacheService billingCacheService;

    public AgencyBillingSummaryDTO getSummary(AgencyBillingQuery query) {
        return getSummary(query, dataScopeService.resolveForCurrentUser());
    }

    public AgencyBillingSummaryDTO getSummary(AgencyBillingQuery query, DataScopeContext scope) {
        validateRange(query);
        return billingCacheService.loadBundle(query, scope).getSummary();
    }

    public AgencyBillingDetailDTO getDetail(AgencyBillingQuery query) {
        return getDetail(query, dataScopeService.resolveForCurrentUser());
    }

    public AgencyBillingDetailDTO getDetail(AgencyBillingQuery query, DataScopeContext scope) {
        if (RecordJsonSupport.isBlank(query.getAgencyKey())) {
            throw new BusinessException(400, "agency_billing.agency_required");
        }
        validateRange(query);
        AgencyBillingBundle bundle = billingCacheService.loadBundle(query, scope);
        return AgencyBillingAggregator.buildDetail(bundle,
                query.getAgencyKey().trim(),
                normalizeKey(query.getWarehouseKey()),
                normalizeKey(query.getCountryKey()));
    }

    public List<AgencyBillingDetailDTO> buildAllDetailsForExport(AgencyBillingQuery query, DataScopeContext scope) {
        validateRange(query);
        return AgencyBillingAggregator.allDetails(billingCacheService.loadBundle(query, scope));
    }

    private static void validateRange(AgencyBillingQuery query) {
        if (query == null || RecordJsonSupport.isBlank(query.getStartDate()) || RecordJsonSupport.isBlank(query.getEndDate())) {
            throw new BusinessException(400, "agency_billing.date_range_required");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(query.getStartDate().trim(), DATE_FMT);
            end = LocalDate.parse(query.getEndDate().trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "agency_billing.invalid_date");
        }
        if (end.isBefore(start)) {
            throw new BusinessException(400, "agency_billing.invalid_date_range");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(400, "agency_billing.range_too_long");
        }
        // warm day list validation only; cache service builds day list on load
        List<String> dayList = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dayList.add(d.format(DATE_FMT));
        }
        if (dayList.isEmpty()) {
            throw new BusinessException(400, "agency_billing.invalid_date_range");
        }
    }

    private static String normalizeKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }
}
