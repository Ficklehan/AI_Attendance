package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.common.PageResult;
import com.attendance.dto.request.AgencyBillingQuery;
import com.attendance.dto.request.EmployeeExportQuery;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.ExportJobDTO;
import com.attendance.dto.response.ExportSummaryDTO;
import com.attendance.entity.ExportJob;
import com.attendance.mapper.ExportJobMapper;
import com.attendance.security.TaskAccessService;
import com.attendance.util.AgencyBillingExcelWriter;
import com.attendance.util.ExcelExportHelper;
import com.attendance.util.ExcelExportHelper.ExcelSheetWriter;
import com.attendance.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class ExportJobService {

    private static final Logger log = LoggerFactory.getLogger(ExportJobService.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static final String TYPE_TASK_LIST = "task_list";
    public static final String TYPE_EMPLOYEE_RECORDS = "employee_records";
    public static final String TYPE_AGENCY_BILLING = "agency_billing";
    public static final String TYPE_EMPLOYEE_LIST = "employee_list";
    public static final String TYPE_WEEKLY_ATTENDANCE = "weekly_attendance";

    @Value("${export.path:./exports}")
    private String exportPath;

    @Value("${export.retention-days:7}")
    private int retentionDays;

    @Autowired
    private ExportJobMapper exportJobMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AgencyBillingService agencyBillingService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    @Qualifier("exportExecutor")
    private Executor exportExecutor;

    public ExportJobDTO createTaskListExport(TaskQuery query) {
        return createExport(TYPE_TASK_LIST, query);
    }

    public ExportJobDTO createEmployeeRecordsExport(TaskQuery query) {
        return createExport(TYPE_EMPLOYEE_RECORDS, query);
    }

    public ExportJobDTO createAgencyBillingExport(AgencyBillingQuery query) {
        String userId = taskAccessService.requireCurrentUserId();
        if (query == null) {
            query = new AgencyBillingQuery();
        }
        ExportJob job = new ExportJob();
        job.setId(IdGenerator.generateId());
        job.setUserId(userId);
        job.setExportType(TYPE_AGENCY_BILLING);
        job.setStatus("pending");
        job.setQueryJson(JSON.toJSONString(query));
        job.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, retentionDays)));
        exportJobMapper.insert(job);

        exportExecutor.execute(() -> runExport(job.getId()));
        return ExportJobDTO.from(job);
    }

    public ExportJobDTO createEmployeeListExport(EmployeeExportQuery query) {
        return createEmployeeExport(TYPE_EMPLOYEE_LIST, query);
    }

    public ExportJobDTO createWeeklyAttendanceExport(EmployeeExportQuery query) {
        return createEmployeeExport(TYPE_WEEKLY_ATTENDANCE, query);
    }

    private ExportJobDTO createEmployeeExport(String exportType, EmployeeExportQuery query) {
        String userId = taskAccessService.requireCurrentUserId();
        if (query == null) {
            query = new EmployeeExportQuery();
        }
        ExportJob job = new ExportJob();
        job.setId(IdGenerator.generateId());
        job.setUserId(userId);
        job.setExportType(exportType);
        job.setStatus("pending");
        job.setQueryJson(JSON.toJSONString(query));
        job.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, retentionDays)));
        exportJobMapper.insert(job);

        exportExecutor.execute(() -> runExport(job.getId()));
        return ExportJobDTO.from(job);
    }

    private ExportJobDTO createExport(String exportType, TaskQuery query) {
        String userId = taskAccessService.requireCurrentUserId();
        if (query == null) {
            query = new TaskQuery();
        }
        taskService.attachListScopeToQuery(query);
        ExportJob job = new ExportJob();
        job.setId(IdGenerator.generateId());
        job.setUserId(userId);
        job.setExportType(exportType);
        job.setStatus("pending");
        job.setQueryJson(JSON.toJSONString(query));
        job.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, retentionDays)));
        exportJobMapper.insert(job);

        exportExecutor.execute(() -> runExport(job.getId()));
        return ExportJobDTO.from(job);
    }

    public PageResult<ExportJobDTO> listJobs(long current, long size, String scope) {
        String userId = taskAccessService.requireCurrentUserId();
        String normalizedScope = normalizeScope(scope);
        long offset = (current - 1) * size;
        List<ExportJobDTO> records = exportJobMapper.selectByUserId(userId, normalizedScope, offset, size).stream()
                .map(ExportJobDTO::from)
                .collect(Collectors.toList());
        long total = exportJobMapper.countByUserId(userId, normalizedScope);
        return PageResult.of(records, total, current, size);
    }

    public ExportSummaryDTO getSummary() {
        String userId = taskAccessService.requireCurrentUserId();
        return new ExportSummaryDTO(exportJobMapper.countActiveByUserId(userId));
    }

    public int clearFinished() {
        String userId = taskAccessService.requireCurrentUserId();
        return exportJobMapper.dismissFinishedByUserId(userId);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.trim().isEmpty() || "active".equalsIgnoreCase(scope)) {
            return "active";
        }
        if ("all".equalsIgnoreCase(scope) || "history".equalsIgnoreCase(scope)) {
            return "all";
        }
        return "active";
    }

    public ExportJobDTO getJob(String jobId) {
        String userId = taskAccessService.requireCurrentUserId();
        ExportJob job = exportJobMapper.selectByIdAndUserId(jobId, userId);
        if (job == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.EXPORT_JOB_NOT_FOUND);
        }
        return ExportJobDTO.from(job);
    }

    public void download(String jobId, HttpServletResponse response) {
        String userId = taskAccessService.requireCurrentUserId();
        ExportJob job = exportJobMapper.selectByIdAndUserId(jobId, userId);
        if (job == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.EXPORT_JOB_NOT_FOUND);
        }
        if (!"completed".equals(job.getStatus()) || job.getFilePath() == null || job.getFilePath().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.EXPORT_JOB_NOT_READY);
        }
        Path path = Paths.get(job.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND);
        }
        try {
            String fileName = job.getFileName() != null ? job.getFileName() : path.getFileName().toString();
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.setContentType(ExcelExportHelper.CONTENT_TYPE);
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            Files.copy(path, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("下载导出文件失败 jobId={}", jobId, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND);
        }
        consumeDownloadedFile(jobId, userId, path);
    }

    private void consumeDownloadedFile(String jobId, String userId, Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("删除已下载导出文件失败 jobId={}", jobId, e);
        }
        try {
            int updated = exportJobMapper.markDownloaded(jobId, userId);
            if (updated > 0) {
                log.info("导出文件已下载并清理 jobId={}", jobId);
            }
        } catch (Exception e) {
            log.warn("更新导出任务下载状态失败 jobId={}", jobId, e);
        }
    }

    private void runExport(String jobId) {
        ExportJob job = exportJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        exportJobMapper.updateStatus(jobId, "running", null);
        String type = job.getExportType();
        com.attendance.security.DataScopeContext scope = taskService.resolveDataScopeForUserId(job.getUserId());
        String prefix = resolveFilePrefix(type);
        String fileName = prefix + "_" + FILE_TS.format(LocalDateTime.now()) + ".xlsx";
        Path dir = Paths.get(exportPath, job.getUserId());
        Path file = dir.resolve(jobId + ".xlsx");

        try {
            long rowCount;
            if (TYPE_TASK_LIST.equals(type)) {
                TaskQuery query = parseQuery(job.getQueryJson());
                try (ExcelSheetWriter writer = ExcelExportHelper.open(file)) {
                    rowCount = taskService.exportTaskListToExcel(
                            scope, query.getStatus(), query.getKeyword(), query.getSearchField(), writer,
                            query.getLocale());
                }
            } else if (TYPE_AGENCY_BILLING.equals(type)) {
                AgencyBillingQuery billingQuery = parseAgencyBillingQuery(job.getQueryJson());
                rowCount = AgencyBillingExcelWriter.write(file,
                        agencyBillingService.buildAllDetailsForExport(billingQuery, scope),
                        billingQuery.getLocale());
            } else if (TYPE_EMPLOYEE_LIST.equals(type)) {
                EmployeeExportQuery empQuery = parseEmployeeExportQuery(job.getQueryJson());
                try (ExcelSheetWriter writer = ExcelExportHelper.open(file)) {
                    rowCount = employeeService.exportEmployeeListToExcel(
                            scope, empQuery.getRegionCodes(), empQuery.getRegionCode(), empQuery.getKeyword(),
                            writer, empQuery.getLocale());
                }
            } else if (TYPE_WEEKLY_ATTENDANCE.equals(type)) {
                EmployeeExportQuery empQuery = parseEmployeeExportQuery(job.getQueryJson());
                try (ExcelSheetWriter writer = ExcelExportHelper.open(file)) {
                    rowCount = employeeService.exportWeeklyAttendanceToExcel(
                            scope, empQuery.getIsoWeek(), empQuery.getRegionCodes(), empQuery.getRegionCode(),
                            empQuery.getKeyword(), writer, empQuery.getLocale());
                }
            } else {
                TaskQuery query = parseQuery(job.getQueryJson());
                rowCount = taskService.exportEmployeeRecordsToExcel(
                        scope, query, file, job.getUserId(), job.getExpiresAt());
            }
            exportJobMapper.updateCompleted(jobId, "completed", fileName, file.toString(), rowCount, null);
            log.info("导出完成 jobId={}, type={}, rows={}", jobId, job.getExportType(), rowCount);
        } catch (Exception e) {
            log.error("导出失败 jobId={}", jobId, e);
            String msg;
            if (e instanceof BusinessException) {
                BusinessException be = (BusinessException) e;
                msg = be.getMessageKey() != null ? be.getMessageKey() : be.getMessage();
            } else {
                msg = describeFailureChain(e);
            }
            if (msg.length() > 900) {
                msg = msg.substring(0, 900);
            }
            exportJobMapper.updateCompleted(jobId, "failed", null, null, 0, msg);
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
    }

    /**
     * 拼出根因链，避免 POI 之类外层异常只留下 "... : null" 而真因被丢弃。
     */
    private String describeFailureChain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 6) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                sb.append(": ").append(current.getMessage().trim());
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
            depth++;
        }
        return sb.length() > 0 ? sb.toString() : e.getClass().getSimpleName();
    }

    private String resolveFilePrefix(String type) {
        if (TYPE_TASK_LIST.equals(type)) {
            return "tasks";
        }
        if (TYPE_AGENCY_BILLING.equals(type)) {
            return "agency_releve";
        }
        if (TYPE_EMPLOYEE_LIST.equals(type)) {
            return "employees";
        }
        if (TYPE_WEEKLY_ATTENDANCE.equals(type)) {
            return "weekly_attendance";
        }
        return "attendance_records";
    }

    private EmployeeExportQuery parseEmployeeExportQuery(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new EmployeeExportQuery();
        }
        try {
            return JSON.parseObject(json, EmployeeExportQuery.class);
        } catch (Exception e) {
            return new EmployeeExportQuery();
        }
    }

    private AgencyBillingQuery parseAgencyBillingQuery(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new AgencyBillingQuery();
        }
        try {
            return JSON.parseObject(json, AgencyBillingQuery.class);
        } catch (Exception e) {
            return new AgencyBillingQuery();
        }
    }

    private TaskQuery parseQuery(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new TaskQuery();
        }
        try {
            return JSON.parseObject(json, TaskQuery.class);
        } catch (Exception e) {
            return new TaskQuery();
        }
    }
}
