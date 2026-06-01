package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeishuSyncService {

    private static final Logger log = LoggerFactory.getLogger(FeishuSyncService.class);

    @Autowired
    private BitableService bitableService;

    @Autowired
    private TaskMapper taskMapper;

    @Async
    public void syncConfirmedTask(String taskId, List<Map<String, Object>> data, String configCountry) {
        try {
            log.info("开始异步飞书同步: taskId={}, configCountry={}, records={}",
                    taskId, configCountry, data.size());
            List<String> recordIds = bitableService.batchWriteRecordsReturningIds(data, configCountry);
            mergeFeishuRecordIds(taskId, recordIds);
            taskMapper.updateTaskSyncStatus(taskId, "synced", null);
            log.info("飞书同步成功: taskId={}, recordIds={}", taskId, recordIds.size());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "飞书同步失败";
            log.error("飞书同步失败: taskId={}, error={}", taskId, msg, e);
            taskMapper.updateTaskSyncStatus(taskId, "sync_failed", truncate(msg, 500));
        }
    }

    @Async
    public void syncCalibratedRecord(String taskId, String rowKey, String configCountry) {
        taskMapper.updateTaskSyncStatus(taskId, "pending", null);
        try {
            log.info("开始校准后飞书更新: taskId={}, rowKey={}, country={}", taskId, rowKey, configCountry);
            JSONObject record = loadRecordByRowKey(taskId, rowKey);
            if (record == null) {
                throw new RuntimeException("未找到校准记录: " + rowKey);
            }

            Map<String, Object> recordMap = new HashMap<>(record);
            recordMap.put("TASK_ID", taskId);

            String feishuRecordId = record.getString("_feishuRecordId");
            if (feishuRecordId == null || feishuRecordId.isBlank()) {
                String no = record.getString("NO");
                feishuRecordId = bitableService.findRecordIdByTaskAndNo(taskId, no, configCountry);
                if (feishuRecordId != null && !feishuRecordId.isBlank()) {
                    record.put("_feishuRecordId", feishuRecordId);
                    persistRecordFeishuId(taskId, rowKey, feishuRecordId);
                }
            }

            if (feishuRecordId == null || feishuRecordId.isBlank()) {
                throw new RuntimeException("未找到飞书多维表对应记录，无法更新（任务id=" + taskId + "）");
            }

            bitableService.updateRecordById(feishuRecordId, recordMap, configCountry);
            taskMapper.updateTaskSyncStatus(taskId, "synced", null);
            log.info("校准飞书更新成功: taskId={}, rowKey={}, feishuRecordId={}", taskId, rowKey, feishuRecordId);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "飞书校准同步失败";
            log.error("校准飞书更新失败: taskId={}, rowKey={}, error={}", taskId, rowKey, msg, e);
            taskMapper.updateTaskSyncStatus(taskId, "sync_failed", truncate(msg, 500));
        }
    }

    private JSONObject loadRecordByRowKey(String taskId, String rowKey) {
        com.attendance.entity.Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            return null;
        }
        String payload = task.getConfirmedData();
        if (payload == null || payload.isBlank()) {
            payload = task.getRawData();
        }
        if (payload == null || payload.isBlank()) {
            return null;
        }
        JSONArray arr = JSON.parseArray(payload);
        if (arr == null) {
            return null;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row != null && rowKey.equals(row.getString("_rowKey"))) {
                return row;
            }
        }
        return null;
    }

    private void persistRecordFeishuId(String taskId, String rowKey, String feishuRecordId) {
        com.attendance.entity.Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            return;
        }
        String payload = task.getConfirmedData();
        if (payload == null || payload.isBlank()) {
            return;
        }
        JSONArray arr = JSON.parseArray(payload);
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row != null && rowKey.equals(row.getString("_rowKey"))) {
                row.put("_feishuRecordId", feishuRecordId);
                break;
            }
        }
        String json = arr.toJSONString();
        taskMapper.updateTaskRecordPayload(taskId, json, json);
    }

    /**
     * 将飞书返回的 record_id 按「未删除」记录顺序写回 confirmed_data / raw_data。
     */
    private void mergeFeishuRecordIds(String taskId, List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return;
        }
        com.attendance.entity.Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            return;
        }
        String payload = task.getConfirmedData();
        if (payload == null || payload.isBlank()) {
            payload = task.getRawData();
        }
        if (payload == null || payload.isBlank()) {
            return;
        }
        JSONArray arr = JSON.parseArray(payload);
        if (arr == null) {
            return;
        }
        int idIndex = 0;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getBoolean("isDeleted"))) {
                continue;
            }
            if (idIndex < recordIds.size()) {
                String id = recordIds.get(idIndex++);
                if (id != null && !id.isBlank()) {
                    row.put("_feishuRecordId", id);
                }
            }
        }
        String json = arr.toJSONString();
        taskMapper.updateTaskRecordPayload(taskId, json, json);
        log.info("已合并飞书 record_id: taskId={}, count={}", taskId, idIndex);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
