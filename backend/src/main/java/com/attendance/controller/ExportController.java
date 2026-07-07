package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.request.AgencyBillingQuery;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.ExportJobDTO;
import com.attendance.dto.response.ExportSummaryDTO;
import com.attendance.service.ExportJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/exports")
@Validated
public class ExportController {

    @Autowired
    private ExportJobService exportJobService;

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
        return Result.success(exportJobService.createEmployeeRecordsExport(query));
    }

    @PostMapping("/agency-billing")
    public Result<ExportJobDTO> exportAgencyBilling(@RequestBody(required = false) AgencyBillingQuery query) {
        if (query == null) {
            query = new AgencyBillingQuery();
        }
        return Result.success(exportJobService.createAgencyBillingExport(query));
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
