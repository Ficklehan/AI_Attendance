package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.request.AgencyBillingQuery;
import com.attendance.dto.request.EmployeeExportQuery;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.ExportJobDTO;
import com.attendance.dto.response.ExportSummaryDTO;
import com.attendance.service.EmployeeService;
import com.attendance.service.ExportJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/exports")
@Validated
public class ExportController {

    @Autowired
    private ExportJobService exportJobService;

    @Autowired
    private EmployeeService employeeService;

    @PostMapping("/task-list")
    public Result<ExportJobDTO> exportTaskList(@RequestBody(required = false) TaskQuery query) {
        if (query == null) {
            query = new TaskQuery();
        }
        return Result.success(exportJobService.createTaskListExport(query));
    }

    @PostMapping("/employee-records")
    public Result<ExportJobDTO> exportEmployeeRecords(@RequestBody(required = false) TaskQuery query) {
        if (query == null) {
            query = new TaskQuery();
        }
        if (query.getImageBaseUrl() == null || query.getImageBaseUrl().trim().isEmpty()) {
            query.setImageBaseUrl(resolveRequestBaseUrl());
        }
        return Result.success(exportJobService.createEmployeeRecordsExport(query));
    }

    /** 以当前请求(经反向代理 X-Forwarded-* 解析)域名+context-path 作为导出图片链接根地址 */
    private String resolveRequestBaseUrl() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/agency-billing")
    public Result<ExportJobDTO> exportAgencyBilling(@RequestBody(required = false) AgencyBillingQuery query) {
        if (query == null) {
            query = new AgencyBillingQuery();
        }
        return Result.success(exportJobService.createAgencyBillingExport(query));
    }

    @PostMapping("/employee-list")
    public Result<ExportJobDTO> exportEmployeeList(@RequestBody(required = false) EmployeeExportQuery query) {
        employeeService.requireEmployeesAccess();
        if (query == null) {
            query = new EmployeeExportQuery();
        }
        return Result.success(exportJobService.createEmployeeListExport(query));
    }

    @PostMapping("/weekly-attendance")
    public Result<ExportJobDTO> exportWeeklyAttendance(@RequestBody(required = false) EmployeeExportQuery query) {
        employeeService.requireEmployeesAccess();
        if (query == null) {
            query = new EmployeeExportQuery();
        }
        return Result.success(exportJobService.createWeeklyAttendanceExport(query));
    }

    @GetMapping
    public Result<PageResult<ExportJobDTO>> listExports(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(defaultValue = "active") String scope) {
        return Result.success(exportJobService.listJobs(current, size, scope));
    }

    @GetMapping("/summary")
    public Result<ExportSummaryDTO> summary() {
        return Result.success(exportJobService.getSummary());
    }

    @PostMapping("/clear")
    public Result<Integer> clearFinished() {
        return Result.success(exportJobService.clearFinished());
    }

    @GetMapping("/{jobId}")
    public Result<ExportJobDTO> getExport(@PathVariable String jobId) {
        return Result.success(exportJobService.getJob(jobId));
    }

    @GetMapping("/{jobId}/download")
    public void download(@PathVariable String jobId, HttpServletResponse response) {
        exportJobService.download(jobId, response);
    }
}
