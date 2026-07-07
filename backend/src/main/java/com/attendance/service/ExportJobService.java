package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.common.PageResult;
import com.attendance.dto.request.AgencyBillingQuery;
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
    }

    private void runExport(String jobId) {
        ExportJob job = exportJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        exportJobMapper.updateStatus(jobId, "running", null);
        TaskQuery query = parseQuery(job.getQueryJson());
        com.attendance.security.DataScopeContext scope = taskService.resolveDataScopeForUserId(job.getUserId());
        String prefix = TYPE_TASK_LIST.equals(job.getExportType()) ? "tasks"
                : TYPE_AGENCY_BILLING.equals(job.getExportType()) ? "agency_releve" : "attendance_records";
        String fileName = prefix + "_" + FILE_TS.format(LocalDateTime.now()) + ".xlsx";
        Path dir = Paths.get(exportPath, job.getUserId());
        Path file = dir.resolve(jobId + ".xlsx");

        try {
            long rowCount;
            if (TYPE_TASK_LIST.equals(job.getExportType())) {
                try (ExcelSheetWriter writer = ExcelExportHelper.open(file)) {
                    rowCount = taskService.exportTaskListToExcel(
                            scope, query.getStatus(), query.getKeyword(), query.getSearchField(), writer,
                            query.getLocale());
                }
            } else if (TYPE_AGENCY_BILLING.equals(job.getExportType())) {
                AgencyBillingQuery billingQuery = parseAgencyBillingQuery(job.getQueryJson());
                rowCount = AgencyBillingExcelWriter.write(file,
                        agencyBillingService.buildAllDetailsForExport(billingQuery, scope),
                        billingQuery.getLocale());
            } else {
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
                msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
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
