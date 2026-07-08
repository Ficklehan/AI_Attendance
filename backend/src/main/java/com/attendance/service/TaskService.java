package com.attendance.service;

import com.attendance.config.CountryCatalog;
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
import com.attendance.util.EmployeeRecordExportImages;
import com.attendance.util.EmployeeRecordExcelWriter;
import com.attendance.util.ExportLocaleSupport;
import com.attendance.mapper.UserMapper;
import com.attendance.security.TaskAccessService;
import com.attendance.util.RecordFeishuPrepareSupport;
import com.attendance.util.RecognizedDateNormalizer;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    @Lazy
    private TaskService self;

    private static final DateTimeFormatter TASK_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int TASK_ID_ALLOC_MAX_ATTEMPTS = 8;

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
    private EmployeeRecordExportImages employeeRecordExportImages;

    @Autowired
    private ConfirmValidationService confirmValidationService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private NightShiftConfigService nightShiftConfigService;

    @Autowired
    @Lazy
    private ReminderScheduleService reminderScheduleService;

    @Autowired
    private TaskRecognitionLifecycleService taskRecognitionLifecycleService;

    @Autowired
    private TaskExcelExportService taskExcelExportService;

    private static final String[] CALIBRATABLE_FIELDS = {
            "NO", "Entrepot", "NOM_PRENOM", "AGENCE_INTERIMAIRE", "HORAIRES_DU_TRAVAIL",
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

    public Task createTask(String fileKey, String promptCountry) {
        String userId = taskAccessService.requireCurrentUserId();
        String datePrefix = LocalDate.now().format(TASK_ID_DATE);
        String lastTaskId = taskMapper.selectMaxTaskIdForDate(datePrefix);
        DuplicateKeyException lastConflict = null;
        String taskId = recordNoGenerator.generate(lastTaskId);
        for (int attempt = 0; attempt < TASK_ID_ALLOC_MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                taskId = recordNoGenerator.nextAfter(taskId);
            }
            try {
                Task task = self.persistNewTask(taskId, userId, fileKey, promptCountry);
                log.info("创建任务成功: taskId={}, userId={}, fileKey={}, promptCountry={}",
                        taskId, userId, fileKey, task.getPromptCountry());
                return task;
            } catch (DuplicateKeyException e) {
                lastConflict = e;
                log.warn("任务号冲突，流水号+1重试: conflictTaskId={}, attempt={}", taskId, attempt + 1);
            }
        }
        log.error("任务号分配失败，连续 {} 次主键冲突", TASK_ID_ALLOC_MAX_ATTEMPTS, lastConflict);
        throw new BusinessException(500, ErrorKeys.SYSTEM_ERROR);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Task persistNewTask(String taskId, String userId, String fileKey, String promptCountry) {
        Task task = new Task();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setFileKey(fileKey);
        task.setStatus("processing");
        task.setSyncStatus("none");
        if (promptCountry != null && !promptCountry.trim().isEmpty()) {
            task.setPromptCountry(com.attendance.util.CountryResolver.normalize(promptCountry.trim()));
        }
        taskMapper.insertTask(task);
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
        maybeRepairTaskRecords(task);
        return task;
    }

    /** 确认后合并逻辑修复前可能写入双倍行；打开详情时按合并结果校正 task_records。 */
    private void maybeRepairTaskRecords(Task task) {
        if (!"confirmed".equalsIgnoreCase(task.getStatus())) {
            return;
        }
        String payload = TaskRecordPayloadResolver.resolvePayload(task);
        if (RecordJsonSupport.isBlank(payload)) {
            return;
        }
        try {
            JSONArray rows = JSON.parseArray(payload);
            int expected = rows != null ? rows.size() : 0;
            long actual = taskRecordMapper.countByTaskId(task.getTaskId());
            if (actual != expected) {
                log.info("task_records 行数与合并载荷不一致，自动修复: taskId={}, actual={}, expected={}",
                        task.getTaskId(), actual, expected);
                taskRecordSyncService.syncFromTaskId(task.getTaskId());
            }
        } catch (Exception e) {
            log.warn("检测 task_records 行数失败: taskId={}", task.getTaskId(), e);
        }
    }

    public Task getTaskByIdInternal(String taskId) {
        return taskMapper.selectTaskByTaskId(taskId);
    }

    public boolean isRecognitionHeartbeatFresh(String taskId, long maxAgeMs) {
        return taskRecognitionLifecycleService.isRecognitionHeartbeatFresh(taskId, maxAgeMs);
    }

    @Transactional
    public void touchRecognitionHeartbeat(String taskId) {
        taskRecognitionLifecycleService.touchRecognitionHeartbeat(taskId);
    }

    public RecognitionCheckpoint loadRecognitionCheckpoint(String taskId) {
        return taskRecognitionLifecycleService.loadRecognitionCheckpoint(taskId);
    }

    @Transactional
    public void saveRecognitionCheckpoint(String taskId, RecognitionCheckpoint checkpoint) {
        taskRecognitionLifecycleService.saveRecognitionCheckpoint(taskId, checkpoint);
    }

    @Transactional
    public void clearRecognitionCheckpoint(String taskId) {
        taskRecognitionLifecycleService.clearRecognitionCheckpoint(taskId);
    }

    public List<String> findStaleProcessingTaskIds(int staleSeconds, int batchSize) {
        return taskRecognitionLifecycleService.findStaleProcessingTaskIds(staleSeconds, batchSize);
    }

    public List<String> findZombieProcessingTaskIds(int zombieMinutes, int batchSize) {
        return taskRecognitionLifecycleService.findZombieProcessingTaskIds(zombieMinutes, batchSize);
    }

    public boolean hasRecognitionWorkStarted(Task task) {
        return taskRecognitionLifecycleService.hasRecognitionWorkStarted(task);
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
        taskRecognitionLifecycleService.updateTaskRawData(taskId, rawData, aiRawOutput);
    }

    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput, RecognitionTrace recognitionTrace) {
        taskRecognitionLifecycleService.updateTaskRawData(taskId, rawData, aiRawOutput, recognitionTrace);
    }

    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput, RecognitionTrace recognitionTrace,
                                  ImageQualityAssessment imageQuality) {
        taskRecognitionLifecycleService.updateTaskRawData(taskId, rawData, aiRawOutput, recognitionTrace, imageQuality);
    }

    @Transactional
    public void updateTaskRecognitionProgress(String taskId, int rowCount, String engineTag) {
        taskRecognitionLifecycleService.updateTaskRecognitionProgress(taskId, rowCount, engineTag);
    }

    @Transactional
    public void updateTaskRawDataProgress(String taskId, String rawData, String aiRawOutput) {
        taskRecognitionLifecycleService.updateTaskRawDataProgress(taskId, rawData, aiRawOutput);
    }

    public TaskProgressDTO getTaskProgress(String taskId) {
        return taskRecognitionLifecycleService.getTaskProgress(taskId);
    }

    private static int countJsonArrayRows(String rawData) {
        return TaskRecognitionLifecycleService.countJsonArrayRows(rawData);
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
        taskRecognitionLifecycleService.writeRecognitionFailureSummary(taskId, errorMessage, errorArgs, recognitionTrace);
        Task task = taskMapper.selectTaskByTaskId(taskId);
        int partialRows = task != null ? countJsonArrayRows(task.getRawData()) : 0;
        taskMapper.updateTaskStatus(taskId, "failed");
        taskRecordSyncService.syncFromTaskId(taskId);
        cancelReminderSchedulesForTask(taskId);
        reminderScheduleService.onTaskStatusChanged(taskId);
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

        String confirmRegionCode = resolveConfirmCountry(countryCode, task);
        for (Map<String, Object> record : data) {
            employeeService.assignEmployeeOnConfirm(record, confirmRegionCode);
        }

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
        cancelReminderSchedulesForTask(taskId);
        reminderScheduleService.onTaskStatusChanged(taskId);

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
        taskRecognitionLifecycleService.prepareTaskForRecognition(taskId, reset);
    }

    @Transactional
    public void prepareTaskForRecognitionInternal(String taskId, boolean reset) {
        taskRecognitionLifecycleService.prepareTaskForRecognitionInternal(taskId, reset);
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
        taskRecognitionLifecycleService.updateTaskAnomalySummary(taskId, anomalySummary);
    }

    @Transactional
    public Map<String, Object> deleteTask(String taskId, String reason) {
        Task task = taskAccessService.requireOwnedTask(taskId);
        String trimmedReason = reason != null ? reason.trim() : "";
        if ("confirmed".equals(task.getStatus())) {
            if (trimmedReason.isEmpty()) {
                throw new BusinessException(400, ErrorKeys.DELETE_REASON_REQUIRED);
            }
            String currentUserId = taskAccessService.requireCurrentUserId();
            User operator = userMapper.selectUserById(currentUserId);
            if (!permissionService.hasPermission(operator, PermissionService.TASK_DELETE_CONFIRMED)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.TASK_DELETE_CONFIRMED_PERMISSION_DENIED);
            }
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("reason", trimmedReason);
        auditDetails.put("taskStatus", task.getStatus());
        auditDetails.put("fileKey", task.getFileKey());
        auditDetails.put("syncStatus", task.getSyncStatus());
        String payload = TaskRecordPayloadResolver.resolvePayload(task);
        auditDetails.put("recordCount", countJsonArrayRows(payload));

        deleteTaskUploadFiles(task);
        taskRecordSyncService.deleteByTaskId(taskId);
        userNotificationService.deleteByTaskId(taskId);
        taskMapper.deleteTaskByTaskId(taskId);
        cancelReminderSchedulesForTask(taskId);
        log.info("删除任务: taskId={}, status={}, reasonLength={}", taskId, task.getStatus(), trimmedReason.length());
        return auditDetails;
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
        cancelReminderSchedulesForTask(taskId);
        reminderScheduleService.onTaskStatusChanged(taskId);
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
        List<Map<String, String>> conditions = taskExcelExportService.parseFilters(searchField, keyword, filters);
        List<TaskRecord> rows = taskRecordMapper.selectRecordPage(scope, status, conditions, offset, size);
        Map<String, Map<String, JSONObject>> taskRowCache = new HashMap<>();
        List<EmployeeRecordDTO> result = new ArrayList<>();
        for (TaskRecord row : rows) {
            result.add(taskExcelExportService.toEmployeeRecord(row, taskRowCache));
        }
        return result;
    }

    public long countEmployeeRecordList(String status, String keyword, String searchField, String filters) {
        DataScopeContext scope = resolveDataScope();
        List<Map<String, String>> conditions = taskExcelExportService.parseFilters(searchField, keyword, filters);
        return taskRecordMapper.countRecords(scope, status, conditions);
    }

    public long exportTaskListToExcel(DataScopeContext scope, String status, String keyword, String searchField,
                                      ExcelSheetWriter writer, String locale) throws IOException {
        return taskExcelExportService.exportTaskListToExcel(scope, status, keyword, searchField, writer, locale);
    }

    public void writeTaskJsonRecordsToExcel(Task task, ExcelSheetWriter writer, String locale) throws IOException {
        taskExcelExportService.writeTaskJsonRecordsToExcel(task, writer, locale);
    }

    public Path createTaskExportTempFile(String taskId, String locale) throws IOException {
        Task task = getTaskForCurrentUser(taskId);
        return taskExcelExportService.createTaskExportTempFile(task, locale);
    }

    public long exportEmployeeRecordsToExcel(DataScopeContext scope, TaskQuery query, Path outputFile,
                                             String exportUserId, LocalDateTime linkExpiresAt) throws IOException {
        return taskExcelExportService.exportEmployeeRecordsToExcel(scope, query, outputFile, exportUserId, linkExpiresAt);
    }

    public Map<String, Object> checkDuplicateNamesAgainstConfirmed(String taskId,
                                                                   List<Map<String, Object>> currentRecords,
                                                                   String scope) {
        taskAccessService.requireViewableTask(taskId);
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
        String paysKey = CountryCatalog.normalizeCountryKey(pays);
        m.put("paysKey", paysKey != null ? paysKey : RecordJsonSupport.upper(pays));
        m.put("entrepotKey", RecordJsonSupport.upper(entrepot));
        m.put("dateKey", RecognizedDateNormalizer.normalizeDate(date));
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

    private void cancelReminderSchedulesForTask(String taskId) {
        try {
            reminderScheduleService.onTaskLeftReminderScope(taskId);
        } catch (Exception e) {
            log.warn("取消提醒计划失败 taskId={}: {}", taskId, e.getMessage());
        }
    }

}
