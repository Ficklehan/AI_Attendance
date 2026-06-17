package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.Task;
import com.attendance.entity.TaskListRow;
import com.attendance.entity.TaskRecord;
import com.attendance.entity.User;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.dto.RecognitionCheckpoint;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.EmployeeRecordDTO;
import com.attendance.dto.response.TaskProgressDTO;
import com.attendance.dto.response.TaskSummaryDTO;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.security.DataScopeContext;
import com.attendance.security.SecurityUtils;
import com.attendance.util.CountryResolver;
import com.attendance.util.ExcelExportHelper;
import com.attendance.util.ExcelExportHelper.ExcelSheetWriter;
import com.attendance.mapper.UserMapper;
import com.attendance.security.TaskAccessService;
import com.attendance.util.RecordFeishuPrepareSupport;
import com.attendance.util.RecognizedFieldSanitizer;
import com.attendance.util.RecognizedTextNormalizer;
import com.attendance.util.RecognitionFailureMessages;
import com.attendance.util.RecordJsonSupport;
import com.attendance.util.RecordNoGenerator;
import com.attendance.util.NightShiftCountryResolver;
import com.attendance.util.SmartMarkNightShiftRefresher;
import com.attendance.util.TaskRecordExportSupport;
import com.attendance.util.TaskRecordPayloadResolver;
import com.attendance.storage.FileStorage;
import com.attendance.util.UploadPathSecurity;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class TaskService {
    
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private RecordNoGenerator recordNoGenerator;

    @Autowired
    private ConfigService configService;

    @Autowired
    private FeishuSyncService feishuSyncService;

    @Autowired
    private FeishuCountryConfigService feishuCountryConfigService;
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private TaskRecordMapper taskRecordMapper;

    @Autowired
    private TaskRecordSyncService taskRecordSyncService;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private ConfirmValidationService confirmValidationService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private NightShiftConfigService nightShiftConfigService;

    private static final String[] CALIBRATABLE_FIELDS = {
            "NO", "Pays", "Entrepot", "NOM_PRENOM", "AGENCE_INTERIMAIRE", "HORAIRES_DU_TRAVAIL",
            "Date", "ARRIVEE", "DEPAR", "PAUSE", "SIGNATURE", "Observations"
    };

    public DataScopeContext resolveDataScope() {
        return dataScopeService.resolveForCurrentUser();
    }

    public DataScopeContext resolveDataScopeForUserId(String userId) {
        return dataScopeService.resolveForUserId(userId);
    }

    public void attachListScopeToQuery(TaskQuery query) {
        if (query == null) {
            return;
        }
        DataScopeContext scope = resolveDataScope();
        query.setAllUsersScope(scope.isAllUsers());
        query.setListScopeUserId(scope.isAllUsers() ? null : SecurityUtils.getCurrentUserId());
    }

    public TaskSummaryDTO getTaskSummary() {
        DataScopeContext scope = resolveDataScope();
        boolean allUsers = scope.isAllUsers();
        List<Map<String, Object>> rows = taskMapper.countTasksGroupByStatus(scope);

        long processing = 0;
        long review = 0;
        long confirmed = 0;
        long failed = 0;
        long cancelled = 0;

        for (Map<String, Object> row : rows) {
            String status = row.get("status") != null ? String.valueOf(row.get("status")) : "";
            long cnt = toLong(row.get("cnt"));
            if ("processing".equals(status)) {
                processing = cnt;
            } else if ("processed".equals(status)) {
                review = cnt;
            } else if ("confirmed".equals(status)) {
                confirmed = cnt;
            } else if ("failed".equals(status)) {
                failed = cnt;
            } else if ("cancelled".equals(status)) {
                cancelled = cnt;
            }
        }

        TaskSummaryDTO dto = new TaskSummaryDTO();
        dto.setProcessing(processing);
        dto.setReview(review);
        dto.setConfirmed(confirmed);
        dto.setFailed(failed);
        dto.setCancelled(cancelled);
        dto.setTotal(processing + review + confirmed + failed + cancelled);
        dto.setAllUsersScope(allUsers);
        return dto;
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Transactional
    public Task createTask(String fileKey) {
        return createTask(fileKey, null);
    }

    @Transactional
    public Task createTask(String fileKey, String promptCountry) {
        String userId = taskAccessService.requireCurrentUserId();
        String lastTaskId = taskMapper.selectLastTaskId();
        String taskId = recordNoGenerator.generate(lastTaskId);

        Task task = new Task();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setFileKey(fileKey);
        task.setStatus("processing");
        task.setSyncStatus("none");
        if (promptCountry != null && !promptCountry.trim().isEmpty()) {
            task.setPromptCountry(promptCountry.trim());
        }

        taskMapper.insertTask(task);
        log.info("创建任务成功: taskId={}, userId={}, fileKey={}, promptCountry={}",
                taskId, userId, fileKey, task.getPromptCountry());

        return task;
    }

    public Task getTaskById(String taskId) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        return task;
    }

    public Task getTaskForCurrentUser(String taskId) {
        Task task = taskAccessService.requireViewableTask(taskId);
        String userId = com.attendance.security.SecurityUtils.getCurrentUserId();
        task.setCanConfirm(taskAccessService.canConfirmTask(userId, task));
        return task;
    }

    public Task getTaskByIdInternal(String taskId) {
        return taskMapper.selectTaskByTaskId(taskId);
    }

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

    /**
     * 识别是否已真正开始（有进度/断点/部分结果），用于区分「仅上传中」的 processing 任务。
     */
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

    public List<TaskListRow> getTaskList(String status, String keyword, String searchField, long offset, long size) {
        DataScopeContext scope = resolveDataScope();
        return taskMapper.selectTaskList(scope, status, keyword, searchField, offset, size);
    }

    public long countTaskList(String status, String keyword, String searchField) {
        DataScopeContext scope = resolveDataScope();
        return taskMapper.countTaskList(scope, status, keyword, searchField);
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

    /** 兼容旧调用：忽略 imageQuality 警告写入 */
    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput, RecognitionTrace recognitionTrace,
                                  ImageQualityAssessment imageQuality) {
        updateTaskRawData(taskId, rawData, aiRawOutput, recognitionTrace);
    }

    /** 仅更新识别进度计数（轮询轻量接口数据源） */
    @Transactional
    public void updateTaskRecognitionProgress(String taskId, int rowCount, String engineTag) {
        taskMapper.updateTaskRecognitionProgress(taskId, rowCount, engineTag);
    }

    /**
     * 识别进行中周期性写入全量 raw_data（降频调用），并更新 progress_row_count。
     */
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

    private static int countJsonArrayRows(String rawData) {
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
    public void failTask(String taskId, String errorMessage) {
        failTask(taskId, errorMessage, null, null);
    }

    @Transactional
    public void failTask(String taskId, String errorMessage, RecognitionTrace recognitionTrace) {
        failTask(taskId, errorMessage, null, recognitionTrace);
    }

    @Transactional
    public void failTask(String taskId, String errorMessage, Map<String, Object> errorArgs,
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
        taskMapper.updateTaskStatus(taskId, "failed");
        taskRecordSyncService.syncFromTaskId(taskId);
        log.warn("任务识别失败: taskId={}, error={}, partialRows={}", taskId, errorMessage, partialRows);
    }

    @Transactional
    public void confirmTask(String taskId, List<Map<String, Object>> data) {
        confirmTask(taskId, data, null);
    }

    @Transactional
    public void confirmTask(String taskId, List<Map<String, Object>> data, String countryCode) {
        Task task = taskAccessService.requireOwnedTask(taskId);
        log.info("开始确认任务: taskId={}, currentStatus={}", taskId, task.getStatus());

        if (!"processed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.TASK_STATUS_CANNOT_CONFIRM);
        }

        if (data != null) {
            for (Map<String, Object> record : data) {
                RecognizedFieldSanitizer.sanitizeRecordPlaceholders(record);
                RecognizedTextNormalizer.normalizeRecordFields(record);
                refreshNightShiftSmartMark(record, task.getPromptCountry());
            }
        }
        confirmValidationService.validateConfirmRecords(data);

        for (Map<String, Object> record : data) {
            RecordFeishuPrepareSupport.prepareRecord(record);
        }

        String currentUserId = taskAccessService.requireCurrentUserId();
        log.info("当前用户: userId={}", currentUserId);
        
        // 查询用户信息，获取飞书用户ID
        User user = userMapper.selectUserById(currentUserId);
        log.info("查询到的用户信息: {}", user);
        String feishuUserId = user != null ? user.getFeishuUserId() : null;
        String userName = user != null ? user.getRealName() : (user != null ? user.getUsername() : "未知用户");
        log.info("用户信息: feishuUserId={}, userName={}", feishuUserId, userName);
        
        for (Map<String, Object> record : data) {
            record.put("TASK_ID", taskId);
            // 优先使用飞书用户ID，如果没有则使用用户名
            if (feishuUserId != null && !feishuUserId.isEmpty()) {
                record.put("UPLOADED_BY", feishuUserId);
            } else {
                record.put("UPLOADED_BY", userName);
            }
            log.info("为记录添加字段: TASK_ID={}, UPLOADED_BY={}", taskId, record.get("UPLOADED_BY"));
        }

        String confirmedData = JSON.toJSONString(data);
        taskMapper.updateTaskConfirmedData(taskId, confirmedData);
        log.info("已保存确认数据: taskId={}, recordCount={}", taskId, data.size());

        List<Map<String, Object>> feishuRecords = new ArrayList<>();
        for (Map<String, Object> record : data) {
            if (!isRecordDeletedForSync(record)) {
                feishuRecords.add(record);
            }
        }

        JSONObject summary = new JSONObject();
        int totalRecords = data.size();
        int validRecords = 0;
        int deletedRecords = 0;
        int anomalyRecords = 0;
        List<String> anomalies = new ArrayList<>();
        String overallRiskLevel = "none";

        for (Map<String, Object> record : data) {
            boolean deleted = Boolean.TRUE.equals(record.get("deleted"));
            if (deleted) {
                deletedRecords++;
            } else {
                validRecords++;
            }

            String riskLevel = record.get("riskLevel") != null ? record.get("riskLevel").toString() : "none";
            if (!"none".equals(riskLevel)) {
                anomalyRecords++;
                if ("high".equals(riskLevel)) {
                    overallRiskLevel = "high";
                } else if ("medium".equals(riskLevel) && !"high".equals(overallRiskLevel)) {
                    overallRiskLevel = "medium";
                }
            }

            Object anomaliesObj = record.get("anomalies");
            if (anomaliesObj instanceof List) {
                for (Object anomaly : (List<?>) anomaliesObj) {
                    if (anomaly != null) {
                        anomalies.add(anomaly.toString());
                    }
                }
            }
        }

        summary.put("totalRecords", totalRecords);
        summary.put("validRecords", validRecords);
        summary.put("deletedRecords", deletedRecords);
        summary.put("anomalyRecords", anomalyRecords);
        summary.put("anomalies", anomalies);
        summary.put("riskLevel", overallRiskLevel);

        taskMapper.updateTaskAnomalySummary(taskId, summary.toJSONString());
        log.info("任务确认成功: taskId={}, recordCount={}", taskId, data.size());
        taskRecordSyncService.syncFromTaskId(taskId);

        String feishuCountry = resolveConfirmCountry(countryCode, task);
        if (feishuCountryConfigService.isSyncEnabled(feishuCountry)) {
            taskMapper.updateTaskSyncStatus(taskId, "pending", null);
            log.info("飞书同步国家: requestCountry={}, taskPromptCountry={}, headerCountry={}",
                    feishuCountry, task.getPromptCountry(), countryCode);
            feishuSyncService.syncConfirmedTask(taskId, feishuRecords, feishuCountry);
        } else {
            taskMapper.updateTaskSyncStatus(taskId, "none", null);
            log.info("该国已关闭飞书多维表同步，仅确认任务: country={}", feishuCountry);
        }
    }

    public void retryFeishuSync(String taskId) {
        Task task = taskAccessService.requireOwnedTask(taskId);
        if (!"confirmed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.FEISHU_RETRY_CONFIRMED_ONLY);
        }
        String syncStatus = task.getSyncStatus() != null ? task.getSyncStatus() : "none";
        if ("synced".equals(syncStatus)) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.FEISHU_ALREADY_SYNCED);
        }
        if (task.getConfirmedData() == null || task.getConfirmedData().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.NO_CONFIRMED_DATA_TO_SYNC);
        }

        String feishuCountry = resolveConfirmCountry(null, task);
        if (!feishuCountryConfigService.isSyncEnabled(feishuCountry)) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.FEISHU_SYNC_DISABLED);
        }

        JSONArray arr = JSON.parseArray(task.getConfirmedData());
        if (arr == null || arr.isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_DATA_EMPTY);
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            Map<String, Object> row = new HashMap<>(arr.getJSONObject(i));
            RecordFeishuPrepareSupport.prepareRecord(row);
            data.add(row);
        }

        taskMapper.updateTaskSyncStatus(taskId, "pending", null);
        log.info("重试飞书同步: taskId={}, feishuCountry={}, records={}", taskId, feishuCountry, data.size());
        feishuSyncService.syncConfirmedTask(taskId, data, feishuCountry);
    }

    private String resolveConfirmCountry(String countryCode, Task task) {
        if (countryCode != null && !countryCode.trim().isEmpty()) {
            return CountryResolver.normalize(countryCode);
        }
        if (task.getPromptCountry() != null && !task.getPromptCountry().trim().isEmpty()) {
            return CountryResolver.normalize(task.getPromptCountry());
        }
        String current = configService.getCurrentCountry();
        if (current != null && !current.trim().isEmpty()) {
            return CountryResolver.normalize(current);
        }
        return "default";
    }

    @Transactional
    public void updateTaskImageUrls(String taskId, List<String> imageUrls) {
        List<String> safeKeys = UploadPathSecurity.validateFileKeys(imageUrls);
        taskMapper.updateTaskImageUrls(taskId, JSON.toJSONString(safeKeys));
        taskRecordSyncService.syncFromTaskId(taskId);
    }

    public List<String> parseImageUrlList(Task task) {
        List<String> urls = new ArrayList<>();
        if (task == null) {
            return urls;
        }
        if (task.getImageUrls() != null && !task.getImageUrls().trim().isEmpty()) {
            try {
                JSONArray array = JSON.parseArray(task.getImageUrls());
                for (int i = 0; i < array.size(); i++) {
                    String entry = array.getString(i);
                    if (entry != null && !entry.trim().isEmpty() && !urls.contains(entry)) {
                        urls.add(entry.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("解析 imageUrls 失败: taskId={}", task.getTaskId(), e);
            }
        }
        if (task.getFileKey() != null && !task.getFileKey().trim().isEmpty() && !urls.contains(task.getFileKey())) {
            urls.add(0, task.getFileKey().trim());
        }
        return urls;
    }

    @Transactional
    public List<String> appendTaskImageUrl(String taskId, String fileKey) {
        Task task = getTaskForCurrentUser(taskId);
        List<String> urls = parseImageUrlList(task);
        if (fileKey != null && !fileKey.trim().isEmpty() && !urls.contains(fileKey)) {
            urls.add(fileKey.trim());
        }
        updateTaskImageUrls(taskId, urls);
        return urls;
    }

    public byte[] readUploadedImageBytes(String fileKey) throws IOException {
        if (fileKey == null || fileKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGE_INVALID);
        }
        taskAccessService.requireFileAccess(fileKey.trim());
        if (!fileStorage.exists(fileKey.trim())) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND,
                    Collections.singletonMap("fileKey", fileKey));
        }
        return fileStorage.readBytes(fileKey.trim());
    }

    @Transactional
    public void prepareTaskForRecognition(String taskId) {
        prepareTaskForRecognition(taskId, true);
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

    public byte[] readUploadedImageBytesForTask(String taskId, String fileKey) throws IOException {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        List<String> urls = parseImageUrlList(task);
        String safeKey = fileKey != null ? fileKey.trim() : "";
        if (safeKey.isEmpty() || !urls.contains(safeKey)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGE_INVALID);
        }
        if (!fileStorage.exists(safeKey)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND,
                    Collections.singletonMap("fileKey", safeKey));
        }
        return fileStorage.readBytes(safeKey);
    }

    @Transactional
    public void updateTaskAnomalySummary(String taskId, String anomalySummary) {
        taskMapper.updateTaskAnomalySummary(taskId, anomalySummary);
    }

    @Transactional
    public void deleteTask(String taskId) {
        Task task = taskAccessService.requireOwnedTask(taskId);
        if ("confirmed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_TASK_CANNOT_DELETE);
        }
        deleteTaskUploadFiles(task);
        taskRecordSyncService.deleteByTaskId(taskId);
        userNotificationService.deleteByTaskId(taskId);
        taskMapper.deleteTaskByTaskId(taskId);
        log.info("删除任务: taskId={}", taskId);
    }

    private void deleteTaskUploadFiles(Task task) {
        if (task == null) {
            return;
        }
        for (String fileKey : parseImageUrlList(task)) {
            if (fileKey == null || fileKey.trim().isEmpty()) {
                continue;
            }
            try {
                if (fileStorage.exists(fileKey.trim())) {
                    fileStorage.delete(fileKey.trim());
                    log.info("删除任务图片: taskId={}, fileKey={}", task.getTaskId(), fileKey);
                }
            } catch (Exception e) {
                log.warn("删除任务图片失败: taskId={}, fileKey={}", task.getTaskId(), fileKey, e);
            }
        }
    }
    
    @Transactional
    public Map<String, Object> calibrateRecord(String taskId, String rowKey, Map<String, Object> updates, String reason) {
        Task task = taskAccessService.requireOwnedTask(taskId);
        if (!"confirmed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.TASK_NOT_CONFIRMED);
        }
        String currentUserId = taskAccessService.requireCurrentUserId();
        User operator = userMapper.selectUserById(currentUserId);
        permissionService.requirePermission(operator, PermissionService.RECORD_CALIBRATE);

        String trimmedReason = reason != null ? reason.trim() : "";
        if (trimmedReason.isEmpty()) {
            throw new BusinessException(400, ErrorKeys.CALIBRATE_REASON_REQUIRED);
        }
        if (rowKey == null || rowKey.trim().isEmpty()) {
            throw new BusinessException(400, ErrorKeys.VALIDATION_FAILED);
        }

        JSONArray records = parseTaskRecordsArray(task);
        int index = findRecordIndexByRowKey(records, rowKey);
        if (index < 0) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.CALIBRATE_RECORD_NOT_FOUND);
        }

        JSONObject record = records.getJSONObject(index);
        Map<String, Map<String, String>> changes = new LinkedHashMap<>();
        for (String field : CALIBRATABLE_FIELDS) {
            if (updates == null || !updates.containsKey(field)) {
                continue;
            }
            String oldVal = normalizeFieldValue(record.get(field));
            String newVal = normalizeFieldValue(updates.get(field));
            if (oldVal.equals(newVal)) {
                continue;
            }
            record.put(field, updates.get(field));
            Map<String, String> diff = new LinkedHashMap<>();
            diff.put("from", oldVal);
            diff.put("to", newVal);
            changes.put(field, diff);
        }

        if (changes.isEmpty()) {
            throw new BusinessException(400, ErrorKeys.CALIBRATE_NO_CHANGES);
        }

        JSONObject entry = new JSONObject();
        entry.put("at", java.time.LocalDateTime.now().toString());
        entry.put("by", currentUserId);
        String byName = operator != null
                ? (operator.getRealName() != null && !operator.getRealName().trim().isEmpty()
                ? operator.getRealName() : operator.getUsername())
                : currentUserId;
        entry.put("byName", byName);
        entry.put("reason", trimmedReason);
        entry.put("changes", changes);

        JSONArray history = record.getJSONArray("_calibrationHistory");
        if (history == null) {
            history = new JSONArray();
        }
        history.add(entry);
        record.put("_calibrationHistory", history);
        record.put("_manualCalibrated", true);
        refreshNightShiftSmartMark(record, task.getPromptCountry());

        String json = records.toJSONString();
        taskMapper.updateTaskRecordPayload(taskId, json, json);
        taskRecordSyncService.syncFromTaskId(taskId);

        Map<String, Object> result = new HashMap<>();
        result.put("rowKey", rowKey);
        result.put("changes", changes);
        result.put("record", record);
        log.info("记录校准完成: taskId={}, rowKey={}, fields={}", taskId, rowKey, changes.keySet());

        String feishuCountry = resolveConfirmCountry(null, task);
        if (feishuCountryConfigService.isSyncEnabled(feishuCountry)) {
            taskMapper.updateTaskSyncStatus(taskId, "pending", null);
            feishuSyncService.syncCalibratedRecord(taskId, rowKey, feishuCountry);
            result.put("syncStatus", "pending");
        } else {
            result.put("syncStatus", task.getSyncStatus() != null ? task.getSyncStatus() : "none");
        }
        return result;
    }

    private JSONArray parseTaskRecordsArray(Task task) {
        String payload = task.getConfirmedData();
        if (RecordJsonSupport.isBlank(payload)) {
            payload = task.getRawData();
        }
        if (RecordJsonSupport.isBlank(payload)) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_DATA_EMPTY);
        }
        try {
            JSONArray arr = JSON.parseArray(payload);
            if (arr == null) {
                throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_DATA_EMPTY);
            }
            return arr;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_DATA_EMPTY);
        }
    }

    private int findRecordIndexByRowKey(JSONArray records, String rowKey) {
        for (int i = 0; i < records.size(); i++) {
            JSONObject row = records.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String key = row.getString("_rowKey");
            if (rowKey.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeFieldValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    @Transactional
    public void cancelTask(String taskId) {
        Task task = taskAccessService.requireOwnedTask(taskId);
        
        if ("cancelled".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.TASK_ALREADY_CANCELLED);
        }
        if ("confirmed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_TASK_CANNOT_CANCEL);
        }

        taskMapper.updateTaskStatus(taskId, "cancelled");
        taskRecordSyncService.syncFromTaskId(taskId);
        log.info("作废任务: taskId={}", taskId);
    }

    public long countRecordsInTask(String taskId) {
        Task task = getTaskForCurrentUser(taskId);
        if (task == null || task.getRawData() == null) {
            return 0;
        }
        try {
            JSONArray records = JSON.parseArray(task.getRawData());
            return records != null ? records.size() : 0;
        } catch (Exception e) {
            log.error("解析记录数失败: taskId={}", taskId, e);
            return 0;
        }
    }

    public List<EmployeeRecordDTO> getEmployeeRecordList(String status, String keyword, String searchField, String filters, long offset, long size) {
        DataScopeContext scope = resolveDataScope();
        List<Map<String, String>> conditions = parseFilters(searchField, keyword, filters);
        List<TaskRecord> rows = taskRecordMapper.selectRecordPage(scope, status, conditions, offset, size);
        Map<String, Map<String, JSONObject>> taskRowCache = new HashMap<>();
        List<EmployeeRecordDTO> result = new ArrayList<>();
        for (TaskRecord row : rows) {
            result.add(toEmployeeRecord(row, taskRowCache));
        }
        return result;
    }

    public long countEmployeeRecordList(String status, String keyword, String searchField, String filters) {
        DataScopeContext scope = resolveDataScope();
        List<Map<String, String>> conditions = parseFilters(searchField, keyword, filters);
        return taskRecordMapper.countRecords(scope, status, conditions);
    }

    private EmployeeRecordDTO toEmployeeRecord(TaskRecord row) {
        return toEmployeeRecord(row, new HashMap<>());
    }

    private EmployeeRecordDTO toEmployeeRecord(TaskRecord row, Map<String, Map<String, JSONObject>> taskRowCache) {
        EmployeeRecordDTO dto = new EmployeeRecordDTO();
        dto.setTaskId(row.getTaskId());
        dto.setFileKey(row.getFileKey());
        dto.setUserName(row.getUserName());
        dto.setTaskStatus(row.getTaskStatus());
        dto.setRecordStatus(row.getTaskStatus());
        dto.setImageUrls(row.getImageUrls());
        dto.setName(row.getEmpName());
        dto.setCountry(row.getCountry());
        dto.setWarehouse(row.getWarehouse());
        dto.setDate(row.getWorkDate());
        dto.setAgency(row.getAgency());
        dto.setShift(row.getShift());
        dto.setArrival(row.getArrival());
        dto.setDeparture(row.getDeparture());
        dto.setPauseMinutes(row.getPauseMinutes());
        dto.setSignature(row.getSignature());
        dto.setObservations(row.getObservations());
        JSONObject exportJson = buildEmployeeRecordExportJson(row, taskRowCache);
        dto.setNo(RecordJsonSupport.pickJson(exportJson, "NO", "No", "no"));
        dto.setPageNum(TaskRecordExportSupport.resolvePageNum(exportJson));
        dto.setWorkHours(TaskRecordExportSupport.formatWorkHours(exportJson));
        dto.setAnomalyDescription(TaskRecordExportSupport.formatAnomalyDescription(exportJson));
        dto.setSmartMark(row.getSmartMark());
        dto.setCreatedAt(row.getTaskCreatedAt() == null ? "" : row.getTaskCreatedAt().toString());
        return dto;
    }

    private JSONObject buildEmployeeRecordExportJson(TaskRecord row, Map<String, Map<String, JSONObject>> taskRowCache) {
        JSONObject export = TaskRecordExportSupport.toExportJson(row);
        JSONObject source = resolveTaskRowJson(row, taskRowCache);
        if (source == null) {
            return export;
        }
        if (source.containsKey("anomalies")) {
            export.put("anomalies", source.get("anomalies"));
        }
        String unreadableKey = RecognizedFieldSanitizer.UNREADABLE_FIELDS_KEY;
        if (source.containsKey(unreadableKey)) {
            export.put(unreadableKey, source.get(unreadableKey));
        }
        if (RecordJsonSupport.isBlank(TaskRecordExportSupport.resolvePageNum(export))) {
            String pageNum = TaskRecordExportSupport.resolvePageNum(source);
            if (!RecordJsonSupport.isBlank(pageNum)) {
                export.put("PAGE_NUM", pageNum);
            }
        }
        if (RecordJsonSupport.isBlank(RecordJsonSupport.pickJson(export, "NO", "No", "no"))) {
            String no = RecordJsonSupport.pickJson(source, "NO", "No", "no");
            if (!RecordJsonSupport.isBlank(no)) {
                export.put("NO", no);
            }
        }
        return export;
    }

    private JSONObject resolveTaskRowJson(TaskRecord row, Map<String, Map<String, JSONObject>> taskRowCache) {
        if (row == null || RecordJsonSupport.isBlank(row.getTaskId())) {
            return null;
        }
        Map<String, JSONObject> rowMap = taskRowCache.computeIfAbsent(row.getTaskId(), this::loadTaskRowJsonMap);
        if (rowMap == null || rowMap.isEmpty()) {
            return null;
        }
        if (!RecordJsonSupport.isBlank(row.getRowKey()) && rowMap.containsKey(row.getRowKey())) {
            return rowMap.get(row.getRowKey());
        }
        return rowMap.get(String.valueOf(row.getRecordIndex()));
    }

    private Map<String, JSONObject> loadTaskRowJsonMap(String taskId) {
        try {
            Task task = taskMapper.selectTaskByTaskId(taskId);
            if (task == null) {
                return Collections.emptyMap();
            }
            String payload = TaskRecordPayloadResolver.resolvePayload(task);
            if (RecordJsonSupport.isBlank(payload)) {
                return Collections.emptyMap();
            }
            JSONArray rows = JSON.parseArray(payload);
            if (rows == null || rows.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, JSONObject> map = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                String rowKey = RecordJsonSupport.pickJson(row, "_rowKey");
                if (!RecordJsonSupport.isBlank(rowKey)) {
                    map.put(rowKey, row);
                }
                map.put(taskId + "_" + i, row);
                map.put(String.valueOf(i), row);
            }
            return map;
        } catch (Exception e) {
            log.warn("加载任务 JSON 失败: taskId={}", taskId, e);
            return Collections.emptyMap();
        }
    }

    private List<Map<String, String>> parseFilters(String searchField, String keyword, String filters) {
        List<Map<String, String>> list = new ArrayList<>();
        if (!RecordJsonSupport.isBlank(filters)) {
            try {
                JSONArray arr = JSON.parseArray(filters);
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        if (item == null) continue;
                        String field = item.getString("field");
                        String value = item.getString("keyword");
                        String filterType = item.getString("filterType");
                        if (RecordJsonSupport.isBlank(value)) continue;
                        Map<String, String> one = new HashMap<>();
                        one.put("field", field == null ? "" : field.trim());
                        one.put("keyword", value.trim());
                        if (!RecordJsonSupport.isBlank(filterType)) {
                            one.put("filterType", filterType.trim());
                        }
                        list.add(one);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 filters 失败，退化为单条件", e);
            }
        }
        if (list.isEmpty() && !RecordJsonSupport.isBlank(keyword)) {
            Map<String, String> one = new HashMap<>();
            one.put("field", searchField == null ? "" : searchField.trim());
            one.put("keyword", keyword.trim());
            list.add(one);
        }
        return list;
    }

    public long exportTaskListToExcel(DataScopeContext scope, String status, String keyword, String searchField,
                                      ExcelSheetWriter writer) throws IOException {
        writer.writeHeader("任务ID", "文件名", "状态", "飞书同步", "同步错误", "操作人", "创建时间");
        long count = 0;
        int offset = 0;
        final int batchSize = 500;
        while (true) {
            List<TaskListRow> tasks = taskMapper.selectTaskList(scope, status, keyword, searchField, offset, batchSize);
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            for (TaskListRow task : tasks) {
                writer.writeRow(
                        ExcelExportHelper.cell(task.getTaskId()),
                        ExcelExportHelper.cell(task.getFileKey()),
                        ExcelExportHelper.cell(task.getStatus()),
                        ExcelExportHelper.cell(task.getSyncStatus()),
                        ExcelExportHelper.cell(task.getSyncError()),
                        ExcelExportHelper.cell(task.getUserName()),
                        ExcelExportHelper.cell(task.getCreatedAt()));
                count++;
            }
            offset += tasks.size();
            if (tasks.size() < batchSize) {
                break;
            }
        }
        return count;
    }

    private static final String[] TASK_JSON_EXPORT_HEADERS = {
            "页码", "NO", "国家", "仓库", "日期", "姓名", "中介机构", "班次",
            "到达时间", "离开时间", "休息(分钟)", "出勤工时", "员工签名", "备注", "异常说明", "标记"
    };

    /**
     * 单任务导出：从 tasks.confirmed_data / raw_data JSON 写入 Excel（任务编辑页下载）
     */
    public void writeTaskJsonRecordsToExcel(Task task, ExcelSheetWriter writer) throws IOException {
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        String data = TaskRecordPayloadResolver.resolvePayload(task);
        if (data == null || data.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.NO_EXPORT_DATA);
        }
        JSONArray records = JSON.parseArray(data);
        writer.writeHeader(TASK_JSON_EXPORT_HEADERS);
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                JSONObject record = records.getJSONObject(i);
                if (record == null) {
                    continue;
                }
                writer.writeRow(
                        ExcelExportHelper.cell(TaskRecordExportSupport.resolvePageNum(record)),
                        ExcelExportHelper.cell(record.getString("NO")),
                        ExcelExportHelper.cell(record.getString("Pays")),
                        ExcelExportHelper.cell(record.getString("Entrepot")),
                        ExcelExportHelper.cell(record.getString("Date")),
                        ExcelExportHelper.cell(record.getString("NOM_PRENOM")),
                        ExcelExportHelper.cell(record.getString("AGENCE_INTERIMAIRE")),
                        ExcelExportHelper.cell(record.getString("HORAIRES_DU_TRAVAIL")),
                        ExcelExportHelper.cell(record.getString("ARRIVEE")),
                        ExcelExportHelper.cell(record.getString("DEPAR")),
                        ExcelExportHelper.cell(record.getInteger("PAUSE")),
                        ExcelExportHelper.cell(TaskRecordExportSupport.formatWorkHours(record)),
                        ExcelExportHelper.cell(record.getString("SIGNATURE")),
                        ExcelExportHelper.cell(record.getString("Observations")),
                        ExcelExportHelper.cell(TaskRecordExportSupport.formatAnomalyDescription(record)),
                        ExcelExportHelper.cell(record.getString("SmartMark")));
            }
        }
    }

    public Path createTaskExportTempFile(String taskId) throws IOException {
        Task task = getTaskForCurrentUser(taskId);
        Path tempFile = Files.createTempFile("attendance-export-", ".xlsx");
        try (ExcelSheetWriter writer = ExcelExportHelper.open(tempFile)) {
            writeTaskJsonRecordsToExcel(task, writer);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.SYSTEM_ERROR);
        }
        return tempFile;
    }

    public long exportEmployeeRecordsToExcel(DataScopeContext scope, String status, String keyword, String searchField,
                                             String filters, ExcelSheetWriter writer) throws IOException {
        writer.writeHeader("任务ID", "操作人", "任务状态", "创建时间", "文件名",
                "页码", "NO", "姓名", "国家", "仓库", "日期", "中介机构", "班次",
                "到达时间", "离开时间", "休息(分钟)", "出勤工时", "员工签名", "备注", "异常说明", "标记");
        List<Map<String, String>> conditionList = parseFilters(searchField, keyword, filters);
        long count = 0;
        int offset = 0;
        final int batchSize = 500;
        Map<String, Map<String, JSONObject>> taskRowCache = new HashMap<>();
        while (true) {
            List<TaskRecord> rows = taskRecordMapper.selectForExport(scope, status, conditionList, offset, batchSize);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (TaskRecord row : rows) {
                EmployeeRecordDTO dto = toEmployeeRecord(row, taskRowCache);
                writer.writeRow(
                        ExcelExportHelper.cell(dto.getTaskId()),
                        ExcelExportHelper.cell(dto.getUserName()),
                        ExcelExportHelper.cell(dto.getTaskStatus()),
                        ExcelExportHelper.cell(dto.getCreatedAt()),
                        ExcelExportHelper.cell(dto.getFileKey()),
                        ExcelExportHelper.cell(dto.getPageNum()),
                        ExcelExportHelper.cell(dto.getNo()),
                        ExcelExportHelper.cell(dto.getName()),
                        ExcelExportHelper.cell(dto.getCountry()),
                        ExcelExportHelper.cell(dto.getWarehouse()),
                        ExcelExportHelper.cell(dto.getDate()),
                        ExcelExportHelper.cell(dto.getAgency()),
                        ExcelExportHelper.cell(dto.getShift()),
                        ExcelExportHelper.cell(dto.getArrival()),
                        ExcelExportHelper.cell(dto.getDeparture()),
                        ExcelExportHelper.cell(dto.getPauseMinutes()),
                        ExcelExportHelper.cell(dto.getWorkHours()),
                        ExcelExportHelper.cell(dto.getSignature()),
                        ExcelExportHelper.cell(dto.getObservations()),
                        ExcelExportHelper.cell(dto.getAnomalyDescription()),
                        ExcelExportHelper.cell(dto.getSmartMark()));
                count++;
            }
            offset += rows.size();
            if (rows.size() < batchSize) {
                break;
            }
        }
        return count;
    }

    public Map<String, Object> checkDuplicateNamesAgainstConfirmed(String taskId,
                                                                   List<Map<String, Object>> currentRecords,
                                                                   String scope) {
        taskAccessService.requireOwnedTask(taskId);
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> hits = new ArrayList<>();

        if (currentRecords == null || currentRecords.isEmpty()) {
            result.put("duplicates", hits);
            return result;
        }

        List<String> statuses = new ArrayList<>();
        statuses.add("confirmed");
        if ("confirmed_and_processing".equalsIgnoreCase(scope)) {
            statuses.add("processed");
            statuses.add("processing");
        }

        List<String> workDates = new ArrayList<>();
        List<String> baseNames = new ArrayList<>();
        for (Map<String, Object> current : currentRecords) {
            Map<String, Object> normalized = toComparableRow(current, taskId);
            if (!RecordJsonSupport.isBlank((String) normalized.get("dateKey"))) {
                String d = (String) normalized.get("dateKey");
                if (!workDates.contains(d)) {
                    workDates.add(d);
                }
            }
            if (!RecordJsonSupport.isBlank((String) normalized.get("baseName"))) {
                String b = (String) normalized.get("baseName");
                if (!baseNames.contains(b)) {
                    baseNames.add(b);
                }
            }
        }
        if (workDates.isEmpty()) {
            result.put("duplicates", hits);
            return result;
        }
        DataScopeContext dataScope = resolveDataScope();
        List<Map<String, Object>> baseline = taskRecordMapper.selectDuplicateBaseline(
                taskId, statuses, workDates, baseNames, dataScope);

        for (int i = 0; i < currentRecords.size(); i++) {
            Map<String, Object> current = currentRecords.get(i);
            Map<String, Object> normalizedCurrent = toComparableRow(current, taskId);
            if (!isDuplicateComparable(normalizedCurrent)) continue;

            List<Map<String, Object>> matches = new ArrayList<>();
            for (Map<String, Object> row : baseline) {
                if (isSameDuplicateGroup(normalizedCurrent, row)) {
                    matches.add(row);
                }
            }
            if (!matches.isEmpty()) {
                Map<String, Object> one = new HashMap<>();
                one.put("index", i);
                one.put("rowKey", String.valueOf(current.get("_rowKey")));
                one.put("baseName", normalizedCurrent.get("baseName"));
                one.put("matches", matches);
                hits.add(one);
            }
        }

        result.put("duplicates", hits);
        return result;
    }

    private static Map<String, Object> toComparableRow(Map<String, Object> row, String sourceTaskId) {
        Map<String, Object> m = new HashMap<>();
        String name = pick(row, "NOM_PRENOM", "NOM", "NAME");
        String baseName = RecordJsonSupport.stripSerialSuffix(name);
        String pays = pick(row, "Pays", "Country", "PAYS");
        String entrepot = pick(row, "Entrepot", "Entrepôt", "Warehouse");
        String date = pick(row, "Date", "DATE");
        String agency = pick(row, "AGENCE_INTERIMAIRE", "AGENCE", "Agency");
        m.put("NO", pick(row, "NO", "No", "no"));
        m.put("Pays", pays);
        m.put("Entrepot", entrepot);
        m.put("Date", date);
        m.put("HORAIRES_DU_TRAVAIL", pick(row, "HORAIRES_DU_TRAVAIL", "SHIFT", "Shift"));
        m.put("ARRIVEE", pick(row, "ARRIVEE", "ARRIVE", "ARRIVAL"));
        m.put("DEPAR", pick(row, "DEPAR", "DEPART", "DEPARTURE"));
        m.put("PAUSE", pick(row, "PAUSE", "PAUS", "Break"));
        m.put("SIGNATURE", pick(row, "SIGNATURE", "CHECKER", "Signature"));
        m.put("Observations", pick(row, "Observations", "OBSERVATIONS", "Remarks"));
        m.put("AGENCE_INTERIMAIRE", agency);
        m.put("NOM_PRENOM", name);
        m.put("baseName", RecordJsonSupport.upper(baseName));
        m.put("paysKey", RecordJsonSupport.upper(pays));
        m.put("entrepotKey", RecordJsonSupport.upper(entrepot));
        m.put("dateKey", date);
        m.put("agencyKey", RecordJsonSupport.upper(agency));
        m.put("sourceTaskId", sourceTaskId);
        return m;
    }

    private static boolean isDuplicateComparable(Map<String, Object> row) {
        return !RecordJsonSupport.isBlank((String) row.get("Date"))
                && !RecordJsonSupport.isBlank((String) row.get("baseName"));
    }

    private static boolean isSameDuplicateGroup(Map<String, Object> a, Map<String, Object> b) {
        return equalsSafe(a.get("paysKey"), b.get("paysKey"))
                && equalsSafe(a.get("entrepotKey"), b.get("entrepotKey"))
                && equalsSafe(a.get("dateKey"), b.get("dateKey"))
                && equalsSafe(a.get("agencyKey"), b.get("agencyKey"))
                && equalsSafe(a.get("baseName"), b.get("baseName"));
    }

    private static boolean equalsSafe(Object a, Object b) {
        String sa = a == null ? "" : String.valueOf(a);
        String sb = b == null ? "" : String.valueOf(b);
        return sa.equals(sb);
    }

    private static String trim(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                String value = trim(row.get(key));
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private void refreshNightShiftSmartMark(Map<String, Object> record, String taskCountry) {
        if (record == null) {
            return;
        }
        JSONObject json = new JSONObject(record);
        String country = NightShiftCountryResolver.resolveFromRecord(record, taskCountry);
        String refreshed = SmartMarkNightShiftRefresher.refresh(
                pickSmartMark(record), json, nightShiftConfigService.getConfigForCountry(country));
        record.put("SmartMark", refreshed);
    }

    private void refreshNightShiftSmartMark(JSONObject record, String taskCountry) {
        if (record == null) {
            return;
        }
        String country = NightShiftCountryResolver.resolveFromRecord(record, taskCountry);
        String refreshed = SmartMarkNightShiftRefresher.refresh(
                RecordJsonSupport.pickJson(record, "SmartMark", "Mark", "smartMark", "标记"),
                record,
                nightShiftConfigService.getConfigForCountry(country));
        record.put("SmartMark", refreshed);
    }

    private static String pickSmartMark(Map<String, Object> record) {
        String mark = pick(record, "SmartMark", "smartMark", "Mark", "mark", "标记");
        return mark.isEmpty() ? "正常" : mark;
    }

    private static boolean isRecordDeletedForSync(Map<String, Object> record) {
        if (record == null) {
            return true;
        }
        if (Boolean.TRUE.equals(record.get("isDeleted")) || Boolean.TRUE.equals(record.get("deleted"))) {
            return true;
        }
        Object markObj = record.get("SmartMark");
        if (markObj == null) {
            markObj = record.get("Mark");
        }
        String mark = markObj != null ? String.valueOf(markObj) : "";
        return mark.contains("已删除");
    }

}
