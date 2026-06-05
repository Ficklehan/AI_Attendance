package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.entity.Task;
import com.attendance.entity.TaskRecord;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.util.SignatureMarkResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将 tasks 内 JSON 记录增量同步到 task_records 行级表。
 */
@Service
public class TaskRecordSyncService {

    private static final Logger log = LoggerFactory.getLogger(TaskRecordSyncService.class);
    private static final int BATCH_SIZE = 200;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskRecordMapper taskRecordMapper;

    @Transactional
    public void syncFromTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            taskRecordMapper.deleteByTaskId(taskId);
            return;
        }
        syncFromTask(task);
    }

    @Transactional
    public void syncFromTask(Task task) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        String payload = task.getConfirmedData();
        if (isBlank(payload)) {
            payload = task.getRawData();
        }
        if (isBlank(payload)) {
            taskRecordMapper.deleteByTaskId(task.getTaskId());
            return;
        }
        JSONArray rows;
        try {
            rows = JSON.parseArray(payload);
        } catch (Exception e) {
            log.warn("同步 task_records 失败，JSON 无效: taskId={}", task.getTaskId(), e);
            return;
        }
        if (rows == null || rows.isEmpty()) {
            taskRecordMapper.deleteByTaskId(task.getTaskId());
            return;
        }

        List<String> activeKeys = new ArrayList<>();
        List<TaskRecord> batch = new ArrayList<>();
        LocalDateTime taskCreatedAt = task.getCreatedAt() != null ? task.getCreatedAt() : LocalDateTime.now();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row == null) {
                continue;
            }
            TaskRecord record = toTaskRecord(task, row, i, taskCreatedAt);
            activeKeys.add(record.getRowKey());
            batch.add(record);
            if (batch.size() >= BATCH_SIZE) {
                taskRecordMapper.upsertBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            taskRecordMapper.upsertBatch(batch);
        }
        taskRecordMapper.deleteByTaskIdExceptKeys(task.getTaskId(), activeKeys);
        log.debug("已增量同步 task_records: taskId={}, rows={}", task.getTaskId(), rows.size());
    }

    @Transactional
    public void deleteByTaskId(String taskId) {
        taskRecordMapper.deleteByTaskId(taskId);
    }

    private TaskRecord toTaskRecord(Task task, JSONObject row, int index, LocalDateTime taskCreatedAt) {
        TaskRecord tr = new TaskRecord();
        tr.setTaskId(task.getTaskId());
        tr.setUserId(task.getUserId());
        String rowKey = pickJson(row, "_rowKey");
        if (isBlank(rowKey)) {
            rowKey = task.getTaskId() + "_" + index;
        }
        tr.setRowKey(rowKey);
        tr.setRecordIndex(index);
        tr.setDeleted(Boolean.TRUE.equals(row.getBoolean("deleted")));
        tr.setTaskStatus(task.getStatus());
        tr.setFileKey(task.getFileKey());
        tr.setImageUrls(task.getImageUrls());
        String name = pickJson(row, "NOM_PRENOM", "NOM", "NAME");
        tr.setEmpNo(pickJson(row, "NO", "No", "no"));
        tr.setEmpName(name);
        tr.setBaseName(stripSerialSuffix(name).toUpperCase(Locale.ROOT));
        String country = pickJson(row, "Pays", "Country", "PAYS");
        tr.setCountry(country);
        tr.setCountryKey(upper(country));
        String warehouse = pickJson(row, "Entrepot", "Entrepôt", "Warehouse");
        tr.setWarehouse(warehouse);
        tr.setWarehouseKey(upper(warehouse));
        String workDate = pickJson(row, "Date", "DATE");
        tr.setWorkDate(workDate);
        String agency = pickJson(row, "AGENCE_INTERIMAIRE", "AGENCE", "Agency");
        tr.setAgency(agency);
        tr.setAgencyKey(upper(agency));
        tr.setShift(pickJson(row, "HORAIRES_DU_TRAVAIL", "SHIFT", "Shift"));
        tr.setArrival(pickJson(row, "ARRIVEE", "ARRIVE", "ARRIVAL"));
        tr.setDeparture(pickJson(row, "DEPAR", "DEPART", "DEPARTURE"));
        tr.setPauseMinutes(pickJson(row, "PAUSE", "PAUS", "Break"));
        String rawSignature = pickJson(row, "SIGNATURE", "CHECKER", "Signature");
        tr.setSignature(SignatureMarkResolver.normalizeLegacySignature(rawSignature));
        tr.setObservations(pickJson(row, "Observations", "OBSERVATIONS", "Remarks"));
        tr.setPageNum(pickJson(row, "PAGE_NUM", "PageNum", "pageNum", "页码"));
        tr.setSmartMark(pickJson(row, "SmartMark", "Mark", "smartMark", "标记"));
        tr.setTaskCreatedAt(taskCreatedAt);
        return tr;
    }

    private static String pickJson(JSONObject row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                Object v = row.get(key);
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty()) {
                        return s;
                    }
                }
            }
        }
        return "";
    }

    private static String upper(String v) {
        return v == null ? "" : v.toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String stripSerialSuffix(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s\\d{2}$", "").trim();
    }
}
