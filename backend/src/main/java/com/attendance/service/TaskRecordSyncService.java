package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.CountryCatalog;
import com.attendance.entity.Task;
import com.attendance.entity.TaskRecord;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.util.RecordJsonSupport;
import com.attendance.util.TaskRecordPayloadResolver;
import com.attendance.util.RecognizedDateNormalizer;
import com.attendance.util.RecognizedTimeNormalizer;
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
    private static final int VARCHAR_32 = 32;

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
        if (!"confirmed".equalsIgnoreCase(task.getStatus())) {
            taskRecordMapper.deleteByTaskId(task.getTaskId());
            return;
        }
        String payload = TaskRecordPayloadResolver.resolvePayload(task);
        if (RecordJsonSupport.isBlank(payload)) {
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
        String rowKey = RecordJsonSupport.pickJson(row, "_rowKey");
        if (RecordJsonSupport.isBlank(rowKey)) {
            rowKey = task.getTaskId() + "_" + index;
        }
        tr.setRowKey(rowKey);
        tr.setRecordIndex(index);
        tr.setDeleted(isRowDeleted(row));
        tr.setTaskStatus(task.getStatus());
        tr.setFileKey(task.getFileKey());
        tr.setImageUrls(task.getImageUrls());
        String name = RecordJsonSupport.pickJson(row, "NOM_PRENOM", "NOM", "NAME");
        tr.setLineNo(RecordJsonSupport.pickJson(row, "NO", "No", "no"));
        Object employeeId = row.get("employeeId");
        if (employeeId instanceof Number) {
            tr.setEmployeeId(((Number) employeeId).longValue());
        } else if (employeeId != null && !String.valueOf(employeeId).trim().isEmpty()) {
            try {
                tr.setEmployeeId(Long.parseLong(String.valueOf(employeeId).trim()));
            } catch (NumberFormatException ignored) {
                tr.setEmployeeId(null);
            }
        }
        String employeeNo = RecordJsonSupport.pickJson(row, "employeeNo", "EMPLOYEE_NO");
        tr.setEmployeeNo(employeeNo);
        tr.setEmpName(name);
        tr.setBaseName(RecordJsonSupport.stripSerialSuffix(name).toUpperCase(Locale.ROOT));
        String country = RecordJsonSupport.pickJson(row, "Pays", "Country", "PAYS");
        tr.setCountry(country);
        tr.setCountryKey(CountryCatalog.normalizeCountryKey(country));
        String warehouse = RecordJsonSupport.pickJson(row, "Entrepot", "Entrepôt", "Warehouse");
        tr.setWarehouse(warehouse);
        tr.setWarehouseKey(RecordJsonSupport.upper(warehouse));
        String workDate = RecordJsonSupport.clampVarchar(
                RecognizedDateNormalizer.normalizeDate(RecordJsonSupport.pickJson(row, "Date", "DATE")),
                VARCHAR_32);
        tr.setWorkDate(workDate);
        String agency = RecordJsonSupport.pickJson(row, "AGENCE_INTERIMAIRE", "AGENCE", "Agency");
        tr.setAgency(agency);
        tr.setAgencyKey(RecordJsonSupport.upper(agency));
        tr.setShift(RecordJsonSupport.clampVarchar(
                RecognizedTimeNormalizer.normalizeShiftSchedule(
                        RecordJsonSupport.pickJson(row, "HORAIRES_DU_TRAVAIL", "SHIFT", "Shift")),
                VARCHAR_32));
        tr.setArrival(RecordJsonSupport.clampVarchar(
                RecognizedTimeNormalizer.normalizeClockTime(
                        RecordJsonSupport.pickJson(row, "ARRIVEE", "ARRIVE", "ARRIVAL")),
                VARCHAR_32));
        tr.setDeparture(RecordJsonSupport.clampVarchar(
                RecognizedTimeNormalizer.normalizeClockTime(
                        RecordJsonSupport.pickJson(row, "DEPAR", "DEPART", "DEPARTURE")),
                VARCHAR_32));
        tr.setPauseMinutes(RecordJsonSupport.clampVarchar(
                RecordJsonSupport.pickJson(row, "PAUSE", "PAUS", "Break"), VARCHAR_32));
        String rawAiSignature = RecordJsonSupport.pickJson(row, "SIGNATURE_RAW", "SIGNATURE", "CHECKER", "Signature");
        tr.setObservations(RecordJsonSupport.pickJson(row, "Observations", "OBSERVATIONS", "Remarks"));
        tr.setPageNum(RecordJsonSupport.clampVarchar(
                RecordJsonSupport.pickJson(row, "PAGE_NUM", "PageNum", "pageNum", "页码"), VARCHAR_32));
        tr.setSmartMark(RecordJsonSupport.pickJson(row, "SmartMark", "Mark", "smartMark", "标记"));
        tr.setExceptionType(RecordJsonSupport.clampVarchar(
                RecordJsonSupport.pickJson(row, "ExceptionType", "exceptionType"), VARCHAR_32));
        tr.setSignature(SignatureMarkResolver.resolveFromAiOutput(
                rawAiSignature,
                tr.isDeleted(),
                tr.getSmartMark(),
                RecordJsonSupport.pickJson(row, "ARRIVEE", "ARRIVE", "ARRIVAL"),
                RecordJsonSupport.pickJson(row, "DEPAR", "DEPART", "DEPARTURE"),
                RecordJsonSupport.pickJson(row, "Mark", "mark")));
        tr.setTaskCreatedAt(taskCreatedAt);
        return tr;
    }

    private static boolean isRowDeleted(JSONObject row) {
        if (row == null) {
            return false;
        }
        if (Boolean.TRUE.equals(row.getBoolean("deleted")) || Boolean.TRUE.equals(row.getBoolean("isDeleted"))) {
            return true;
        }
        return SignatureMarkResolver.isRowDeletedForSignature(
                false, RecordJsonSupport.pickJson(row, "SmartMark", "Mark", "smartMark", "标记"));
    }

}
