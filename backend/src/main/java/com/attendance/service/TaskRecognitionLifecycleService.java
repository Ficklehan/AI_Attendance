package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.dto.RecognitionCheckpoint;
import com.attendance.dto.response.TaskProgressDTO;
import com.attendance.entity.Task;
import com.attendance.mapper.TaskMapper;
import com.attendance.security.TaskAccessService;
import com.attendance.util.RecognitionFailureMessages;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 识别生命周期：断点续做、心跳、进度写入与轮询。
 */
@Service
public class TaskRecognitionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TaskRecognitionLifecycleService.class);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    private TaskRecordSyncService taskRecordSyncService;

    public boolean isRecognitionHeartbeatFresh(String taskId, long maxAgeMs) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null || task.getRecognitionHeartbeatAt() == null) {
            return false;
        }
        long ageMs = java.time.Duration.between(
                task.getRecognitionHeartbeatAt(),
                java.time.LocalDateTime.now()).toMillis();
        return ageMs >= 0 && ageMs < maxAgeMs;
    }

    @Transactional
    public void touchRecognitionHeartbeat(String taskId) {
        taskMapper.touchRecognitionHeartbeat(taskId);
    }

    public RecognitionCheckpoint loadRecognitionCheckpoint(String taskId) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            return RecognitionCheckpoint.empty();
        }
        return RecognitionCheckpoint.fromJson(task.getRecognitionCheckpoint());
    }

    @Transactional
    public void saveRecognitionCheckpoint(String taskId, RecognitionCheckpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        taskMapper.updateRecognitionCheckpoint(taskId, checkpoint.toJson());
    }

    @Transactional
    public void clearRecognitionCheckpoint(String taskId) {
        taskMapper.clearRecognitionCheckpoint(taskId);
    }

    public List<String> findStaleProcessingTaskIds(int staleSeconds, int batchSize) {
        return taskMapper.selectStaleProcessingTaskIds(
                Math.max(30, staleSeconds), Math.max(1, batchSize));
    }

    public List<String> findZombieProcessingTaskIds(int zombieMinutes, int batchSize) {
        return taskMapper.selectZombieProcessingTaskIds(
                Math.max(1, zombieMinutes), Math.max(1, batchSize));
    }

    public boolean hasRecognitionWorkStarted(Task task) {
        if (task == null) {
            return false;
        }
        if (task.getProgressRowCount() != null && task.getProgressRowCount() > 0) {
            return true;
        }
        if (countJsonArrayRows(task.getRawData()) > 0) {
            return true;
        }
        RecognitionCheckpoint cp = RecognitionCheckpoint.fromJson(task.getRecognitionCheckpoint());
        return cp.getImageIndex() > 0 || cp.getRecordCount() > 0 || cp.getRetryCount() > 0;
    }

    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput) {
        updateTaskRawData(taskId, rawData, aiRawOutput, null);
    }

    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput, RecognitionTrace recognitionTrace) {
        if (recognitionTrace != null) {
            JSONObject summary = new JSONObject();
            summary.put("recognitionTrace", recognitionTrace.toJson());
            taskMapper.updateTaskAnomalySummary(taskId, summary.toJSONString());
        }
        int rowCount = countJsonArrayRows(rawData);
        taskMapper.updateTaskRawData(taskId, rawData, aiRawOutput, rowCount);
        log.info("更新任务AI解析结果: taskId={}, recordCount={}", taskId, rowCount);
        taskRecordSyncService.syncFromTaskId(taskId);
    }

    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput, RecognitionTrace recognitionTrace,
                                  ImageQualityAssessment imageQuality) {
        updateTaskRawData(taskId, rawData, aiRawOutput, recognitionTrace);
    }

    @Transactional
    public void updateTaskRecognitionProgress(String taskId, int rowCount, String engineTag) {
        taskMapper.updateTaskRecognitionProgress(taskId, rowCount, engineTag);
    }

    @Transactional
    public void updateTaskRawDataProgress(String taskId, String rawData, String aiRawOutput) {
        int rowCount = countJsonArrayRows(rawData);
        taskMapper.updateTaskRawDataProgress(taskId, rawData, aiRawOutput, rowCount);
    }

    public TaskProgressDTO getTaskProgress(String taskId) {
        taskAccessService.requireTaskAccessForProgress(taskId);
        TaskProgressDTO dto = taskMapper.selectTaskProgress(taskId);
        if (dto == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        String summary = dto.getAnomalySummaryRaw();
        dto.setAnomalySummaryRaw(null);
        if (summary != null && !summary.trim().isEmpty()) {
            try {
                JSONObject obj = JSON.parseObject(summary);
                if (obj != null && obj.get("error") != null) {
                    String err = RecognitionFailureMessages.toClientMessage(String.valueOf(obj.get("error")));
                    dto.setProgressError(err);
                    JSONObject args = obj.getJSONObject("errorArgs");
                    if (args != null && !args.isEmpty()) {
                        dto.setProgressErrorArgs(args);
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return dto;
    }

    public static int countJsonArrayRows(String rawData) {
        if (rawData == null || rawData.trim().isEmpty()) {
            return 0;
        }
        try {
            JSONArray arr = JSON.parseArray(rawData);
            return arr != null ? arr.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Transactional
    public void prepareTaskForRecognition(String taskId, boolean reset) {
        taskAccessService.requireOwnedTask(taskId);
        prepareTaskForRecognitionInternal(taskId, reset);
    }

    @Transactional
    public void prepareTaskForRecognitionInternal(String taskId, boolean reset) {
        taskMapper.updateTaskStatus(taskId, "processing");
        if (reset) {
            taskMapper.updateTaskRawDataProgress(taskId, "[]", "mimo", 0);
            clearRecognitionCheckpoint(taskId);
        }
        touchRecognitionHeartbeat(taskId);
    }

    @Transactional
    public void updateTaskAnomalySummary(String taskId, String anomalySummary) {
        taskMapper.updateTaskAnomalySummary(taskId, anomalySummary);
    }

    /** 识别失败时写入异常摘要（不含状态流转，由 TaskService 负责） */
    @Transactional
    public void writeRecognitionFailureSummary(String taskId, String errorMessage, Map<String, Object> errorArgs,
                                               RecognitionTrace recognitionTrace) {
        JSONObject summary = new JSONObject();
        summary.put("error", errorMessage);
        if (errorArgs != null && !errorArgs.isEmpty()) {
            summary.put("errorArgs", errorArgs);
        }
        if (recognitionTrace != null) {
            summary.put("recognitionTrace", recognitionTrace.toJson());
        }
        taskMapper.updateTaskAnomalySummary(taskId, summary.toJSONString());
        Task task = taskMapper.selectTaskByTaskId(taskId);
        int partialRows = task != null ? countJsonArrayRows(task.getRawData()) : 0;
        if (partialRows <= 0) {
            taskMapper.updateTaskRawDataProgress(taskId, "[]", "", 0);
        }
    }
}
