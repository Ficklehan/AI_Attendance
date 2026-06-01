package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.entity.AuditLog;
import com.attendance.service.AuditLogService;
import com.attendance.security.AdminAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/audit")
public class AuditController {
    
    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AdminAuthService adminAuthService;

    @GetMapping
    public Result<PageResult<AuditLog>> getAuditLogList(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {

        adminAuthService.requireAdmin();
        long offset = (current - 1) * size;
        List<AuditLog> records = auditLogService.getAuditLogList(
            userId, action, startDate, endDate, offset, size
        );
        long total = auditLogService.countAuditLogList(userId, action, startDate, endDate);
        
        return Result.success(PageResult.of(records, total, current, size));
    }
}