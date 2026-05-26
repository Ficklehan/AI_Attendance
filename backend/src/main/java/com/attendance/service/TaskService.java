package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.entity.Task;
import com.attendance.entity.User;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.UserMapper;
import com.attendance.security.SecurityUtils;
import com.attendance.util.RecordNoGenerator;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {
    
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private RecordNoGenerator recordNoGenerator;

    @Autowired
    private BitableService bitableService;

    @Autowired
    private ConfigService configService;
    
    @Autowired
    private UserMapper userMapper;

    @Transactional
    public Task createTask(String fileKey) {
        String userId = SecurityUtils.getCurrentUserId();
        String lastTaskId = taskMapper.selectLastTaskId();
        String taskId = recordNoGenerator.generate(lastTaskId);

        Task task = new Task();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setFileKey(fileKey);
        task.setStatus("processing");

        taskMapper.insertTask(task);
        log.info("创建任务成功: taskId={}, userId={}, fileKey={}", taskId, userId, fileKey);

        return task;
    }

    public Task getTaskById(String taskId) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在");
        }
        return task;
    }

    public List<Task> getTaskList(String status, String keyword, String searchField, long offset, long size) {
        String userId = SecurityUtils.getCurrentUserId();
        String keywordField = "userName".equals(searchField) ? null : searchField;
        return taskMapper.selectTaskList(userId, status, keyword, keywordField, offset, size);
    }

    public long countTaskList(String status, String keyword, String searchField) {
        String userId = SecurityUtils.getCurrentUserId();
        String keywordField = "userName".equals(searchField) ? null : searchField;
        return taskMapper.selectTaskList(userId, status, keyword, keywordField, 0, 0).size();
    }

    @Transactional
    public void updateTaskRawData(String taskId, String rawData, String aiRawOutput) {
        taskMapper.updateTaskRawData(taskId, rawData, aiRawOutput);
        log.info("更新任务AI解析结果: taskId={}, recordCount={}", 
                 taskId, rawData != null ? JSON.parseArray(rawData).size() : 0);
    }

    @Transactional
    public void confirmTask(String taskId, List<Map<String, Object>> data) {
        Task task = getTaskById(taskId);
        log.info("开始确认任务: taskId={}, currentStatus={}", taskId, task.getStatus());

        if (!"processed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, "任务状态不允许确认");
        }

        String currentUserId = SecurityUtils.getCurrentUserId();
        log.info("当前用户: userId={}", currentUserId);
        
        if (currentUserId == null || currentUserId.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "用户未登录或登录已过期");
        }
        
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
        
        taskMapper.updateTaskStatus(taskId, "confirmed");
        log.info("任务确认成功: taskId={}, recordCount={}", taskId, data.size());
        
        try {
            String countryCode = configService.getConfigValue("current_country", "default");
            log.info("开始飞书同步: countryCode={}, recordCount={}", countryCode, data.size());
            bitableService.batchWriteRecords(data, countryCode);
            log.info("飞书同步成功: taskId={}, recordCount={}, countryCode={}", taskId, data.size(), countryCode);
        } catch (Exception e) {
            log.error("飞书同步失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    @Transactional
    public void updateTaskImageUrls(String taskId, List<String> imageUrls) {
        taskMapper.updateTaskImageUrls(taskId, JSON.toJSONString(imageUrls));
    }

    @Transactional
    public void updateTaskAnomalySummary(String taskId, String anomalySummary) {
        taskMapper.updateTaskAnomalySummary(taskId, anomalySummary);
    }

    @Transactional
    public void deleteTask(String taskId) {
        Task task = getTaskById(taskId);
        String userId = SecurityUtils.getCurrentUserId();
        
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限删除该任务");
        }

        taskMapper.deleteTaskByTaskId(taskId);
        log.info("删除任务: taskId={}", taskId);
    }
    
    @Transactional
    public void cancelTask(String taskId) {
        Task task = getTaskById(taskId);
        
        if ("cancelled".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, "任务已作废");
        }

        taskMapper.updateTaskStatus(taskId, "cancelled");
        log.info("作废任务: taskId={}", taskId);
    }

    public long countRecordsInTask(String taskId) {
        Task task = getTaskById(taskId);
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
}
