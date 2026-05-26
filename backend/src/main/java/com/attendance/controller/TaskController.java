package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.request.ConfirmTaskRequest;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.TaskListDTO;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.service.AuditLogService;
import com.attendance.service.TaskService;
import com.attendance.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
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
    private UserService userService;

    @GetMapping
    public Result<PageResult<TaskListDTO>> getTaskList(TaskQuery query) {
        List<Task> tasks = taskService.getTaskList(
            query.getStatus(), 
            query.getKeyword(),
            query.getSearchField(),
            query.getOffset(), 
            query.getSize()
        );
        
        long total = taskService.countTaskList(query.getStatus(), query.getKeyword(), query.getSearchField());
        
        List<TaskListDTO> records = new ArrayList<>();
        for (Task task : tasks) {
            String userName = null;
            try {
                User user = userService.getUserById(task.getUserId());
                userName = user.getRealName() != null ? user.getRealName() : user.getUsername();
            } catch (Exception e) {
                log.debug("获取用户名失败: {}", e.getMessage());
            }
            records.add(new TaskListDTO(task, userName));
        }
        
        return Result.success(PageResult.of(records, total, query.getCurrent(), query.getSize()));
    }

    @GetMapping("/{taskId}")
    public Result<Task> getTaskDetail(@PathVariable String taskId) {
        Task task = taskService.getTaskById(taskId);
        return Result.success(task);
    }

    @PostMapping("/{taskId}/confirm")
    public Result<Void> confirmTask(@PathVariable String taskId, 
                                    @Valid @RequestBody ConfirmTaskRequest request) {
        log.info("收到任务确认请求: taskId={}, recordsCount={}", taskId, request.getData().size());
        taskService.confirmTask(taskId, request.getData());
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

    @DeleteMapping("/{taskId}")
    public Result<Void> deleteTask(@PathVariable String taskId) {
        taskService.deleteTask(taskId);
        auditLogService.log("TASK_DELETED", "task", taskId, null);
        return Result.success(null, "任务删除成功");
    }
    
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(@PathVariable String taskId) {
        taskService.cancelTask(taskId);
        auditLogService.log("TASK_CANCELLED", "task", taskId, null);
        return Result.success(null, "任务作废成功");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getTaskStats() {
        log.info("获取任务统计");
        
        // 获取各种状态的任务数量
        long totalTasks = taskService.countTaskList(null, null, null);
        long pendingTasks = taskService.countTaskList("PENDING,RECOGNIZING", null, null);
        long completedTasks = taskService.countTaskList("COMPLETED,SUBMITTED", null, null);
        
        // 获取总记录数（需要遍历所有任务）
        long totalRecords = 0;
        List<Task> allTasks = taskService.getTaskList(null, null, null, 0, 1000);
        for (Task task : allTasks) {
            if (task.getRawData() != null) {
                totalRecords += taskService.countRecordsInTask(task.getTaskId());
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalTasks);
        stats.put("pending", pendingTasks);
        stats.put("completed", completedTasks);
        stats.put("records", totalRecords);
        
        log.info("统计结果: total={}, pending={}, completed={}, records={}", 
                totalTasks, pendingTasks, completedTasks, totalRecords);
        
        return Result.success(stats);
    }
}
