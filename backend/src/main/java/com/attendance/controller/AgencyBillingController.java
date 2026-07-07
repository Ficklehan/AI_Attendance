package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.request.AgencyBillingQuery;
import com.attendance.dto.response.AgencyBillingDetailDTO;
import com.attendance.dto.response.AgencyBillingSummaryDTO;
import com.attendance.service.AgencyBillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agency-billing")
public class AgencyBillingController {

    @Autowired
    private AgencyBillingService agencyBillingService;

    @GetMapping("/summary")
    public Result<AgencyBillingSummaryDTO> summary(AgencyBillingQuery query) {
        return Result.success(agencyBillingService.getSummary(query));
    }

    @GetMapping("/detail")
    public Result<AgencyBillingDetailDTO> detail(AgencyBillingQuery query) {
        return Result.success(agencyBillingService.getDetail(query));
    }
}
