package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.EmployeeRecordDTO;
import com.attendance.dto.response.TaskSummaryDTO;
import com.attendance.mapper.TaskMapper;
import com.attendance.security.AdminAuthService;
import com.attendance.security.SecurityUtils;
import com.attendance.util.ExcelExportHelper;
import com.attendance.util.ExcelExportHelper.ExcelSheetWriter;
import com.attendance.mapper.UserMapper;
import com.attendance.security.TaskAccessService;
import com.attendance.util.RecordConfirmValidator;
import com.attendance.util.RecordNoGenerator;
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
    private UserMapper userMapper;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private PermissionService permissionService;

    private static final String[] CALIBRATABLE_FIELDS = {
            "NO", "Pays", "Entrepot", "NOM_PRENOM", "AGENCE_INTERIMAIRE", "HORAIRES_DU_TRAVAIL",
            "Date", "ARRIVEE", "DEPAR", "PAUSE", "SIGNATURE", "Observations"
    };

    /**
     * User id filter for task lists/exports: null = all users (admin only).
     */
    public String resolveListScopeUserId() {
        taskAccessService.requireCurrentUserId();
        if (adminAuthService.isCurrentUserAdmin()) {
            return null;
        }
        return SecurityUtils.getCurrentUserId();
    }

    public String resolveListScopeUserIdForExport(TaskQuery query, String jobOwnerUserId) {
        if (query != null && Boolean.TRUE.equals(query.getAllUsersScope())) {
            return null;
        }
        if (query != null && query.getListScopeUserId() != null && !query.getListScopeUserId().isBlank()) {
            return query.getListScopeUserId();
        }
        return jobOwnerUserId;
    }

    public void attachListScopeToQuery(TaskQuery query) {
        if (query == null) {
            return;
        }
        if (adminAuthService.isCurrentUserAdmin()) {
            query.setAllUsersScope(true);
            query.setListScopeUserId(null);
        } else {
            query.setAllUsersScope(false);
            query.setListScopeUserId(taskAccessService.requireCurrentUserId());
        }
    }

    public TaskSummaryDTO getTaskSummary() {
        String scopeUserId = resolveListScopeUserId();
        boolean allUsers = scopeUserId == null;
        List<Map<String, Object>> rows = taskMapper.countTasksGroupByStatus(scopeUserId);

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
        if (promptCountry != null && !promptCountry.isBlank()) {
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
        return taskAccessService.requireOwnedTask(taskId);
    }

    public List<Task> getTaskList(String status, String keyword, String searchField, long offset, long size) {
        String userId = resolveListScopeUserId();
        return taskMapper.selectTaskList(userId, status, keyword, searchField, offset, size);
    }

    public long countTaskList(String status, String keyword, String searchField) {
        String userId = resolveListScopeUserId();
        return taskMapper.countTaskList(userId, status, keyword, searchField);
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
        taskMapper.updateTaskRawData(taskId, rawData, aiRawOutput);
        log.info("更新任务AI解析结果: taskId={}, recordCount={}",
                taskId, rawData != null ? JSON.parseArray(rawData).size() : 0);
    }

    /** 异步识别过程中周期性写入，便于小程序轮询展示条数 */
    @Transactional
    public void updateTaskRawDataProgress(String taskId, String rawData, String aiRawOutput) {
        taskMapper.updateTaskRawDataProgress(taskId, rawData, aiRawOutput);
    }

    @Transactional
    public void failTask(String taskId, String errorMessage) {
        failTask(taskId, errorMessage, null);
    }

    @Transactional
    public void failTask(String taskId, String errorMessage, RecognitionTrace recognitionTrace) {
        JSONObject summary = new JSONObject();
        summary.put("error", errorMessage);
        if (recognitionTrace != null) {
            summary.put("recognitionTrace", recognitionTrace.toJson());
        }
        taskMapper.updateTaskAnomalySummary(taskId, summary.toJSONString());
        taskMapper.updateTaskStatus(taskId, "failed");
        log.warn("任务识别失败: taskId={}, error={}", taskId, errorMessage);
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

        RecordConfirmValidator.validateConfirmRecords(data);

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
        log.info("已保存确认数据: taskId={}", taskId);

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
        taskMapper.updateTaskSyncStatus(taskId, "pending", null);
        log.info("任务确认成功: taskId={}, recordCount={}", taskId, data.size());

        String feishuCountry = resolveConfirmCountry(countryCode, task);
        log.info("飞书同步国家: requestCountry={}, taskPromptCountry={}, headerCountry={}",
                feishuCountry, task.getPromptCountry(), countryCode);
        feishuSyncService.syncConfirmedTask(taskId, new ArrayList<>(data), feishuCountry);
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
        if (task.getConfirmedData() == null || task.getConfirmedData().isBlank()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.NO_CONFIRMED_DATA_TO_SYNC);
        }

        JSONArray arr = JSON.parseArray(task.getConfirmedData());
        if (arr == null || arr.isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, ErrorKeys.CONFIRMED_DATA_EMPTY);
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            data.add(arr.getJSONObject(i));
        }

        taskMapper.updateTaskSyncStatus(taskId, "pending", null);
        String feishuCountry = resolveConfirmCountry(null, task);
        log.info("重试飞书同步: taskId={}, feishuCountry={}, records={}", taskId, feishuCountry, data.size());
        feishuSyncService.syncConfirmedTask(taskId, data, feishuCountry);
    }

    private String resolveConfirmCountry(String countryCode, Task task) {
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode.trim().toUpperCase();
        }
        if (task.getPromptCountry() != null && !task.getPromptCountry().isBlank()) {
            return task.getPromptCountry().trim().toUpperCase();
        }
        String current = configService.getCurrentCountry();
        if (current != null && !current.isBlank()) {
            return current.trim().toUpperCase();
        }
        return "DEFAULT";
    }

    @Transactional
    public void updateTaskImageUrls(String taskId, List<String> imageUrls) {
        taskMapper.updateTaskImageUrls(taskId, JSON.toJSONString(imageUrls));
    }

    public List<String> parseImageUrlList(Task task) {
        List<String> urls = new ArrayList<>();
        if (task == null) {
            return urls;
        }
        if (task.getImageUrls() != null && !task.getImageUrls().isBlank()) {
            try {
                JSONArray array = JSON.parseArray(task.getImageUrls());
                for (int i = 0; i < array.size(); i++) {
                    String entry = array.getString(i);
                    if (entry != null && !entry.isBlank() && !urls.contains(entry)) {
                        urls.add(entry.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("解析 imageUrls 失败: taskId={}", task.getTaskId(), e);
            }
        }
        if (task.getFileKey() != null && !task.getFileKey().isBlank() && !urls.contains(task.getFileKey())) {
            urls.add(0, task.getFileKey().trim());
        }
        return urls;
    }

    @Transactional
    public List<String> appendTaskImageUrl(String taskId, String fileKey) {
        Task task = getTaskForCurrentUser(taskId);
        List<String> urls = parseImageUrlList(task);
        if (fileKey != null && !fileKey.isBlank() && !urls.contains(fileKey)) {
            urls.add(fileKey.trim());
        }
        updateTaskImageUrls(taskId, urls);
        return urls;
    }

    public byte[] readUploadedImageBytes(String fileKey) throws IOException {
        if (fileKey == null || fileKey.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.IMAGE_INVALID);
        }
        taskAccessService.requireFileAccess(fileKey.trim());
        Path base = Paths.get("./uploads").toAbsolutePath().normalize();
        Path file = base.resolve(fileKey.trim()).normalize();
        if (!file.startsWith(base)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.INVALID_FILE_PATH);
        }
        if (!Files.exists(file)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND,
                    Map.of("fileKey", fileKey));
        }
        return Files.readAllBytes(file);
    }

    @Transactional
    public void prepareTaskForRecognition(String taskId) {
        taskAccessService.requireOwnedTask(taskId);
        taskMapper.updateTaskStatus(taskId, "processing");
        taskMapper.updateTaskRawDataProgress(taskId, "[]", "mimo");
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
        taskMapper.deleteTaskByTaskId(taskId);
        log.info("删除任务: taskId={}", taskId);
    }

    private void deleteTaskUploadFiles(Task task) {
        if (task == null) {
            return;
        }
        Path base = Paths.get("./uploads").toAbsolutePath().normalize();
        for (String fileKey : parseImageUrlList(task)) {
            if (fileKey == null || fileKey.isBlank()) {
                continue;
            }
            try {
                Path file = base.resolve(fileKey.trim()).normalize();
                if (!file.startsWith(base)) {
                    log.warn("跳过非法图片路径: taskId={}, fileKey={}", task.getTaskId(), fileKey);
                    continue;
                }
                if (Files.deleteIfExists(file)) {
                    log.info("删除任务图片: taskId={}, fileKey={}", task.getTaskId(), fileKey);
                }
            } catch (IOException e) {
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
        if (rowKey == null || rowKey.isBlank()) {
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
                ? (operator.getRealName() != null && !operator.getRealName().isBlank()
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

        String json = records.toJSONString();
        taskMapper.updateTaskRecordPayload(taskId, json, json);

        Map<String, Object> result = new HashMap<>();
        result.put("rowKey", rowKey);
        result.put("changes", changes);
        result.put("record", record);
        log.info("记录校准完成: taskId={}, rowKey={}, fields={}", taskId, rowKey, changes.keySet());

        String feishuCountry = resolveConfirmCountry(null, task);
        taskMapper.updateTaskSyncStatus(taskId, "pending", null);
        feishuSyncService.syncCalibratedRecord(taskId, rowKey, feishuCountry);

        result.put("syncStatus", "pending");
        return result;
    }

    private JSONArray parseTaskRecordsArray(Task task) {
        String payload = task.getConfirmedData();
        if (isBlank(payload)) {
            payload = task.getRawData();
        }
        if (isBlank(payload)) {
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
        String userId = resolveListScopeUserId();
        List<Task> tasks = taskMapper.selectTasksForRecordView(userId, status);
        List<EmployeeRecordDTO> all = collectEmployeeRecords(tasks, keyword, searchField, filters);
        int from = Math.max(0, (int) offset);
        int to = Math.min(all.size(), from + Math.max(0, (int) size));
        if (from >= to) {
            return new ArrayList<>();
        }
        return all.subList(from, to);
    }

    public long countEmployeeRecordList(String status, String keyword, String searchField, String filters) {
        String userId = resolveListScopeUserId();
        List<Task> tasks = taskMapper.selectTasksForRecordView(userId, status);
        return collectEmployeeRecords(tasks, keyword, searchField, filters).size();
    }

    private List<EmployeeRecordDTO> collectEmployeeRecords(List<Task> tasks, String keyword, String searchField, String filters) {
        List<EmployeeRecordDTO> all = new ArrayList<>();
        List<Map<String, String>> conditionList = parseFilters(searchField, keyword, filters);
        Map<String, String> userNameCache = new HashMap<>();
        for (Task task : tasks) {
            String payload = task.getConfirmedData();
            if (isBlank(payload)) {
                payload = task.getRawData();
            }
            if (isBlank(payload)) {
                continue;
            }
            JSONArray rows;
            try {
                rows = JSON.parseArray(payload);
            } catch (Exception e) {
                log.warn("解析任务记录失败，跳过 taskId={}", task.getTaskId(), e);
                continue;
            }
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                EmployeeRecordDTO dto = toEmployeeRecord(task, row, userNameCache);
                if (matchesEmployeeSearch(dto, conditionList)) {
                    all.add(dto);
                }
            }
        }
        return all;
    }

    private EmployeeRecordDTO toEmployeeRecord(Task task, Map<String, Object> row, Map<String, String> userNameCache) {
        EmployeeRecordDTO dto = new EmployeeRecordDTO();
        dto.setTaskId(task.getTaskId());
        dto.setFileKey(task.getFileKey());
        dto.setUserName(resolveUserName(task.getUserId(), userNameCache));
        dto.setTaskStatus(task.getStatus());
        dto.setRecordStatus(task.getStatus());
        dto.setImageUrls(task.getImageUrls());
        dto.setNo(pick(row, "NO", "No", "no"));
        dto.setName(pick(row, "NOM_PRENOM", "NOM", "NAME"));
        dto.setCountry(pick(row, "Pays", "Country", "PAYS"));
        dto.setWarehouse(pick(row, "Entrepot", "Entrepôt", "Warehouse"));
        dto.setDate(pick(row, "Date", "DATE"));
        dto.setAgency(pick(row, "AGENCE_INTERIMAIRE", "AGENCE", "Agency"));
        dto.setShift(pick(row, "HORAIRES_DU_TRAVAIL", "SHIFT", "Shift"));
        dto.setArrival(pick(row, "ARRIVEE", "ARRIVE", "ARRIVAL"));
        dto.setDeparture(pick(row, "DEPAR", "DEPART", "DEPARTURE"));
        dto.setPauseMinutes(pick(row, "PAUSE", "PAUS", "Break"));
        dto.setSignature(pick(row, "SIGNATURE", "CHECKER", "Signature"));
        dto.setObservations(pick(row, "Observations", "OBSERVATIONS", "Remarks"));
        dto.setCreatedAt(task.getCreatedAt() == null ? "" : task.getCreatedAt().toString());
        return dto;
    }

    private boolean matchesEmployeeSearch(EmployeeRecordDTO dto, List<Map<String, String>> conditionList) {
        if (conditionList == null || conditionList.isEmpty()) {
            return true;
        }
        for (Map<String, String> cond : conditionList) {
            String field = cond.get("field");
            String needle = cond.get("keyword");
            if (isBlank(needle)) {
                continue;
            }
            String value = isBlank(field) ? allFieldValue(dto) : fieldValue(dto, field);
            if (!value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private String allFieldValue(EmployeeRecordDTO dto) {
        return String.join(" ",
                nullToEmpty(dto.getTaskId()),
                nullToEmpty(dto.getFileKey()),
                nullToEmpty(dto.getUserName()),
                nullToEmpty(dto.getTaskStatus()),
                nullToEmpty(dto.getCreatedAt()),
                nullToEmpty(dto.getNo()),
                nullToEmpty(dto.getName()),
                nullToEmpty(dto.getCountry()),
                nullToEmpty(dto.getWarehouse()),
                nullToEmpty(dto.getDate()),
                nullToEmpty(dto.getAgency()),
                nullToEmpty(dto.getShift()),
                nullToEmpty(dto.getArrival()),
                nullToEmpty(dto.getDeparture()),
                nullToEmpty(dto.getPauseMinutes()),
                nullToEmpty(dto.getSignature()),
                nullToEmpty(dto.getObservations())
        );
    }

    private String fieldValue(EmployeeRecordDTO dto, String field) {
        switch (field) {
            case "taskId":
                return nullToEmpty(dto.getTaskId());
            case "fileKey":
                return nullToEmpty(dto.getFileKey());
            case "userName":
                return nullToEmpty(dto.getUserName());
            case "status":
                return nullToEmpty(dto.getTaskStatus());
            case "createdAt":
                return nullToEmpty(dto.getCreatedAt());
            case "NO":
                return nullToEmpty(dto.getNo());
            case "NOM_PRENOM":
                return nullToEmpty(dto.getName());
            case "Pays":
                return nullToEmpty(dto.getCountry());
            case "Entrepot":
                return nullToEmpty(dto.getWarehouse());
            case "Date":
                return nullToEmpty(dto.getDate());
            case "AGENCE_INTERIMAIRE":
                return nullToEmpty(dto.getAgency());
            case "HORAIRES_DU_TRAVAIL":
                return nullToEmpty(dto.getShift());
            case "ARRIVEE":
                return nullToEmpty(dto.getArrival());
            case "DEPAR":
                return nullToEmpty(dto.getDeparture());
            case "PAUSE":
                return nullToEmpty(dto.getPauseMinutes());
            case "SIGNATURE":
                return nullToEmpty(dto.getSignature());
            case "Observations":
                return nullToEmpty(dto.getObservations());
            default:
                return "";
        }
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private List<Map<String, String>> parseFilters(String searchField, String keyword, String filters) {
        List<Map<String, String>> list = new ArrayList<>();
        if (!isBlank(filters)) {
            try {
                JSONArray arr = JSON.parseArray(filters);
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        if (item == null) continue;
                        String field = item.getString("field");
                        String value = item.getString("keyword");
                        if (isBlank(value)) continue;
                        Map<String, String> one = new HashMap<>();
                        one.put("field", field == null ? "" : field.trim());
                        one.put("keyword", value.trim());
                        list.add(one);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 filters 失败，退化为单条件", e);
            }
        }
        if (list.isEmpty() && !isBlank(keyword)) {
            Map<String, String> one = new HashMap<>();
            one.put("field", searchField == null ? "" : searchField.trim());
            one.put("keyword", keyword.trim());
            list.add(one);
        }
        return list;
    }

    private String resolveUserName(String userId, Map<String, String> cache) {
        if (isBlank(userId)) return "";
        if (cache.containsKey(userId)) return cache.get(userId);
        String name = userId;
        try {
            User user = userMapper.selectUserById(userId);
            if (user != null) {
                name = !isBlank(user.getRealName()) ? user.getRealName() : nullToEmpty(user.getUsername());
            }
        } catch (Exception e) {
            log.debug("获取用户名称失败 userId={}", userId, e);
        }
        cache.put(userId, name);
        return name;
    }

    public long exportTaskListToExcel(String userId, String status, String keyword, String searchField,
                                      ExcelSheetWriter writer) throws IOException {
        writer.writeHeader("任务ID", "文件名", "状态", "飞书同步", "同步错误", "操作人", "创建时间");
        long count = 0;
        int offset = 0;
        final int batchSize = 500;
        Map<String, String> userCache = new HashMap<>();
        while (true) {
            List<Task> tasks = taskMapper.selectTaskList(userId, status, keyword, searchField, offset, batchSize);
            if (tasks == null || tasks.isEmpty()) {
                break;
            }
            for (Task task : tasks) {
                String userName = resolveUserName(task.getUserId(), userCache);
                writer.writeRow(
                        ExcelExportHelper.cell(task.getTaskId()),
                        ExcelExportHelper.cell(task.getFileKey()),
                        ExcelExportHelper.cell(task.getStatus()),
                        ExcelExportHelper.cell(task.getSyncStatus()),
                        ExcelExportHelper.cell(task.getSyncError()),
                        ExcelExportHelper.cell(userName),
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

    public long exportEmployeeRecordsToExcel(String userId, String status, String keyword, String searchField,
                                             String filters, ExcelSheetWriter writer) throws IOException {
        writer.writeHeader("任务ID", "操作人", "任务状态", "创建时间", "工号", "姓名", "国家", "仓库", "日期",
                "中介机构", "班次", "到达", "离开", "休息(分钟)", "签名", "备注", "文件名");
        List<Task> tasks = taskMapper.selectTasksForRecordView(userId, status);
        List<Map<String, String>> conditionList = parseFilters(searchField, keyword, filters);
        Map<String, String> userNameCache = new HashMap<>();
        long count = 0;
        for (Task task : tasks) {
            String payload = task.getConfirmedData();
            if (isBlank(payload)) {
                payload = task.getRawData();
            }
            if (isBlank(payload)) {
                continue;
            }
            JSONArray rows;
            try {
                rows = JSON.parseArray(payload);
            } catch (Exception e) {
                log.warn("导出时解析任务记录失败，跳过 taskId={}", task.getTaskId(), e);
                continue;
            }
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                EmployeeRecordDTO dto = toEmployeeRecord(task, row, userNameCache);
                if (!matchesEmployeeSearch(dto, conditionList)) {
                    continue;
                }
                writer.writeRow(
                        ExcelExportHelper.cell(dto.getTaskId()),
                        ExcelExportHelper.cell(dto.getUserName()),
                        ExcelExportHelper.cell(dto.getTaskStatus()),
                        ExcelExportHelper.cell(dto.getCreatedAt()),
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
                        ExcelExportHelper.cell(dto.getSignature()),
                        ExcelExportHelper.cell(dto.getObservations()),
                        ExcelExportHelper.cell(dto.getFileKey()));
                count++;
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

        List<Task> confirmedTasks = taskMapper.selectTasksForDuplicateByStatuses(taskId, statuses);
        List<Map<String, Object>> baseline = new ArrayList<>();
        for (Task t : confirmedTasks) {
            try {
                JSONArray arr = JSON.parseArray(t.getConfirmedData());
                if (arr == null) continue;
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    if (row == null) continue;
                    baseline.add(toComparableRow(row, t.getTaskId()));
                }
            } catch (Exception e) {
                log.warn("解析确认数据失败，跳过 taskId={}", t.getTaskId(), e);
            }
        }

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
        String baseName = stripSerialSuffix(name);
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
        m.put("baseName", upper(baseName));
        m.put("paysKey", upper(pays));
        m.put("entrepotKey", upper(entrepot));
        m.put("dateKey", date);
        m.put("agencyKey", upper(agency));
        m.put("sourceTaskId", sourceTaskId);
        return m;
    }

    private static boolean isDuplicateComparable(Map<String, Object> row) {
        return !isBlank((String) row.get("Date")) && !isBlank((String) row.get("baseName"));
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

    private static String upper(String v) {
        return v == null ? "" : v.toUpperCase();
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String stripSerialSuffix(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s\\d{2}$", "").trim();
    }
}
