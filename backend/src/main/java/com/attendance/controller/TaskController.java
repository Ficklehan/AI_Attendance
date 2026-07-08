package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.request.CalibrateRecordRequest;
import com.attendance.dto.request.ConfirmTaskRequest;
import com.attendance.dto.request.DeleteTaskRequest;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.EmployeeRecordDTO;
import com.attendance.dto.response.TaskListDTO;
import com.attendance.dto.response.TaskProgressDTO;
import com.attendance.dto.response.TaskSummaryDTO;
import com.attendance.entity.Task;
import com.attendance.entity.TaskListRow;
import com.attendance.service.AuditLogService;
import com.attendance.service.ConfigService;
import com.attendance.service.TaskService;
import com.attendance.service.UserService;
import com.attendance.util.CountryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tasks")
@Validated
public class TaskController {
    
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private TaskService taskService;

    @Autowired
    private AuditLogService auditLogService;
    
    @Autowired
    private ConfigService configService;

    @GetMapping
    public Result<PageResult<TaskListDTO>> getTaskList(TaskQuery query) {
        List<TaskListRow> tasks = taskService.getTaskList(
            query.getStatus(), 
            query.getKeyword(),
            query.getSearchField(),
            query.getOffset(), 
            query.getSize()
        );
        
        long total = taskService.countTaskList(query.getStatus(), query.getKeyword(), query.getSearchField());
        
        List<TaskListDTO> records = new ArrayList<>();
        for (TaskListRow task : tasks) {
            records.add(new TaskListDTO(task));
        }
        
        return Result.success(PageResult.of(records, total, query.getCurrent(), query.getSize()));
    }

    @GetMapping("/{taskId}/progress")
    public Result<TaskProgressDTO> getTaskProgress(@PathVariable String taskId) {
        return Result.success(taskService.getTaskProgress(taskId));
    }

    @GetMapping("/{taskId:^(?!records$)(?!stats$)(?!summary$).+}")
    public Result<Task> getTaskDetail(@PathVariable String taskId) {
        Task task = taskService.getTaskForCurrentUser(taskId);
        return Result.success(task);
    }

    @GetMapping("/records")
    public Result<PageResult<EmployeeRecordDTO>> getEmployeeRecords(TaskQuery query) {
        List<EmployeeRecordDTO> records = taskService.getEmployeeRecordList(
                query.getStatus(),
                query.getKeyword(),
                query.getSearchField(),
                query.getFilters(),
                query.getOffset(),
                query.getSize()
        );
        long total = taskService.countEmployeeRecordList(
                query.getStatus(),
                query.getKeyword(),
                query.getSearchField(),
                query.getFilters()
        );
        return Result.success(PageResult.of(records, total, query.getCurrent(), query.getSize()));
    }

    @PostMapping("/{taskId}/duplicate-check")
    public Result<Map<String, Object>> duplicateCheck(@PathVariable String taskId,
                                                      @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) request.get("records");
        String scope = request.get("scope") != null ? String.valueOf(request.get("scope")) : "confirmed_only";
        Map<String, Object> data = taskService.checkDuplicateNamesAgainstConfirmed(taskId, records, scope);
        return Result.success(data);
    }

    @PostMapping("/{taskId}/confirm")
    public Result<Void> confirmTask(@PathVariable String taskId,
                                    @Valid @RequestBody ConfirmTaskRequest request,
                                    @RequestHeader(value = "X-Country", required = false) String countryHeader,
                                    @RequestParam(value = "country", required = false) String countryParam) {
        String country = CountryResolver.resolve(countryHeader, countryParam, configService);
        log.info("收到任务确认请求: taskId={}, recordsCount={}, country={}", taskId, request.getData().size(), country);
        taskService.confirmTask(taskId, request.getData(), country);
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            taskService.updateTaskImageUrls(taskId, request.getImageUrls());
        }
        if (request.getAnomalySummary() != null) {
            taskService.updateTaskAnomalySummary(taskId, request.getAnomalySummary());
        }
        auditLogService.log("TASK_CONFIRMED", "task", taskId, request.getData().size());
        log.info("任务确认完成: taskId={}", taskId);
        return Result.success(null, "任务确认成功");
    }

    @PostMapping("/{taskId}/delete")
    public Result<Void> deleteTask(@PathVariable String taskId,
                                   @Valid @RequestBody DeleteTaskRequest request) {
        Map<String, Object> auditDetails = taskService.deleteTask(taskId, request.getReason());
        auditLogService.log("TASK_DELETED", "task", taskId, auditDetails);
        return Result.success(null, "任务删除成功");
    }
    
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(@PathVariable String taskId) {
        taskService.cancelTask(taskId);
        auditLogService.log("TASK_CANCELLED", "task", taskId, null);
        return Result.success(null, "任务作废成功");
    }

    @PostMapping("/{taskId}/retry-sync")
    public Result<Void> retryFeishuSync(@PathVariable String taskId) {
        taskService.retryFeishuSync(taskId);
        auditLogService.log("TASK_SYNC_RETRY", "task", taskId, null);
        return Result.success(null, "已开始重新同步飞书");
    }

    @PostMapping("/{taskId}/calibrate-record")
    public Result<Map<String, Object>> calibrateRecord(@PathVariable String taskId,
                                                      @Valid @RequestBody CalibrateRecordRequest request) {
        Map<String, Object> result = taskService.calibrateRecord(
                taskId, request.getRowKey(), request.getUpdates(), request.getReason());
        auditLogService.log("RECORD_CALIBRATED", "task", taskId, result);
        return Result.success(result, "校准已保存");
    }

    @GetMapping("/summary")
    public Result<TaskSummaryDTO> getTaskSummary() {
        return Result.success(taskService.getTaskSummary());
    }

    /**
     * @deprecated 遗留兼容接口；请使用 GET /tasks/summary。将于后续版本移除。
     */
    @Deprecated
    @GetMapping("/stats")
    public Result<Map<String, Object>> getTaskStats() {
        TaskSummaryDTO summary = taskService.getTaskSummary();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", summary.getTotal());
        stats.put("processing", summary.getProcessing());
        stats.put("pending", summary.getReview());
        stats.put("review", summary.getReview());
        stats.put("completed", summary.getConfirmed());
        stats.put("confirmed", summary.getConfirmed());
        stats.put("failed", summary.getFailed());
        stats.put("cancelled", summary.getCancelled());
        stats.put("allUsersScope", summary.isAllUsersScope());
        stats.put("records", 0);
        return Result.success(stats);
    }
}
