package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.request.TaskQuery;
import com.attendance.dto.response.EmployeeRecordDTO;
import com.attendance.entity.Task;
import com.attendance.entity.TaskListRow;
import com.attendance.entity.TaskRecord;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.security.DataScopeContext;
import com.attendance.util.EmployeeRecordExportImages;
import com.attendance.util.EmployeeRecordExcelWriter;
import com.attendance.util.ExcelExportHelper;
import com.attendance.util.ExcelExportHelper.ExcelSheetWriter;
import com.attendance.util.ExportLocaleSupport;
import com.attendance.util.RecordJsonSupport;
import com.attendance.util.RecognizedFieldSanitizer;
import com.attendance.util.TaskRecordExportSupport;
import com.attendance.util.TaskRecordPayloadResolver;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 任务与员工记录 Excel 导出 */
@Service
public class TaskExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(TaskExcelExportService.class);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskRecordMapper taskRecordMapper;

    @Autowired
    private EmployeeRecordExportImages employeeRecordExportImages;

    private static final String[] EMPLOYEE_RECORD_EXPORT_BASE_HEADERS = {
            "任务ID", "操作人", "任务状态", "创建时间", "文件名",
            "页码", "NO", "姓名", "国家", "仓库", "日期", "中介机构", "班次",
            "到达时间", "离开时间", "休息(分钟)", "出勤工时", "员工签名", "备注", "异常说明", "标记"
    };

    private static final int EMPLOYEE_RECORD_BASE_COLUMN_COUNT = EMPLOYEE_RECORD_EXPORT_BASE_HEADERS.length;

    public EmployeeRecordDTO toEmployeeRecord(TaskRecord row) {
        return toEmployeeRecord(row, new HashMap<>());
    }

    public EmployeeRecordDTO toEmployeeRecord(TaskRecord row, Map<String, Map<String, JSONObject>> taskRowCache) {
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

    public List<Map<String, String>> parseFilters(String searchField, String keyword, String filters) {
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
                                      ExcelSheetWriter writer, String locale) throws IOException {
        String resolvedLocale = ExportLocaleSupport.resolveLocale(locale);
        writer.writeHeader(ExportLocaleSupport.headers(resolvedLocale, "taskList.headers"));
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
                        ExcelExportHelper.cell(ExportLocaleSupport.formatTaskStatus(resolvedLocale, task.getStatus())),
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
    public void writeTaskJsonRecordsToExcel(Task task, ExcelSheetWriter writer, String locale) throws IOException {
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        String data = TaskRecordPayloadResolver.resolvePayload(task);
        if (data == null || data.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.NO_EXPORT_DATA);
        }
        String resolvedLocale = ExportLocaleSupport.resolveLocale(locale);
        JSONArray records = JSON.parseArray(data);
        writer.writeHeader(ExportLocaleSupport.headers(resolvedLocale, "taskJson.headers"));
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

    public Path createTaskExportTempFile(Task task, String locale) throws IOException {
        Path tempFile = Files.createTempFile("attendance-export-", ".xlsx");
        try (ExcelSheetWriter writer = ExcelExportHelper.open(tempFile)) {
            writeTaskJsonRecordsToExcel(task, writer, locale);
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

    public long exportEmployeeRecordsToExcel(DataScopeContext scope, TaskQuery query, Path outputFile,
                                             String exportUserId, LocalDateTime linkExpiresAt) throws IOException {
        boolean includeThumbnails = query != null && Boolean.TRUE.equals(query.getIncludeThumbnails());
        String status = query != null ? query.getStatus() : null;
        String keyword = query != null ? query.getKeyword() : null;
        String searchField = query != null ? query.getSearchField() : null;
        String filters = query != null ? query.getFilters() : null;
        long expEpoch = linkExpiresAt != null
                ? linkExpiresAt.atZone(ZoneId.systemDefault()).toEpochSecond()
                : LocalDateTime.now().plusDays(7).atZone(ZoneId.systemDefault()).toEpochSecond();
        String resolvedLocale = query != null ? ExportLocaleSupport.resolveLocale(query.getLocale()) : ExportLocaleSupport.defaultLocale();

        try (EmployeeRecordExcelWriter writer = EmployeeRecordExcelWriter.open(
                outputFile, includeThumbnails, EMPLOYEE_RECORD_BASE_COLUMN_COUNT,
                ExportLocaleSupport.text(resolvedLocale, "sheet.attendanceRecords"))) {
            writer.writeHeader(buildEmployeeRecordExportHeaders(resolvedLocale));
            return writeEmployeeRecordExportRows(
                    scope, status, keyword, searchField, filters, writer, exportUserId, expEpoch, includeThumbnails);
        }
    }

    private String[] buildEmployeeRecordExportHeaders(String locale) {
        String[] base = ExportLocaleSupport.headers(locale, "employeeRecords.headers");
        String[] headers = new String[base.length + 2];
        System.arraycopy(base, 0, headers, 0, base.length);
        headers[base.length] = ExportLocaleSupport.text(locale, "employeeRecords.colImages");
        headers[base.length + 1] = ExportLocaleSupport.text(locale, "employeeRecords.colLinks");
        return headers;
    }

    private long writeEmployeeRecordExportRows(DataScopeContext scope, String status, String keyword,
                                               String searchField, String filters,
                                               EmployeeRecordExcelWriter writer,
                                               String exportUserId, long expEpoch, boolean includeThumbnails)
            throws IOException {
        List<Map<String, String>> conditionList = parseFilters(searchField, keyword, filters);
        long count = 0;
        int offset = 0;
        final int batchSize = 500;
        Map<String, Map<String, JSONObject>> taskRowCache = new HashMap<>();
        Map<String, List<String>> taskImageKeysCache = new HashMap<>();
        while (true) {
            List<TaskRecord> rows = taskRecordMapper.selectForExport(scope, status, conditionList, offset, batchSize);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (TaskRecord row : rows) {
                EmployeeRecordDTO dto = toEmployeeRecord(row, taskRowCache);
                List<String> imageKeys = resolveExportImageKeys(row, dto, taskImageKeysCache);
                List<String> imageUrls = employeeRecordExportImages.buildSignedImageUrls(
                        imageKeys, exportUserId, expEpoch);
                String[] baseCells = toEmployeeRecordExportCells(dto);
                List<EmployeeRecordExportImages.ExportImage> exportImages = new ArrayList<>();
                if (includeThumbnails) {
                    int limit = Math.min(imageKeys.size(), employeeRecordExportImages.getMaxThumbnailsPerRow());
                    for (int i = 0; i < limit; i++) {
                        EmployeeRecordExportImages.ExportImage image =
                                employeeRecordExportImages.readExportImage(imageKeys.get(i));
                        if (image != null) {
                            exportImages.add(image);
                        }
                    }
                }
                writer.writeRecordRow(baseCells, imageUrls, exportImages);
                count++;
            }
            offset += rows.size();
            if (rows.size() < batchSize) {
                break;
            }
        }
        return count;
    }

    private List<String> resolveExportImageKeys(TaskRecord row, EmployeeRecordDTO dto,
                                                Map<String, List<String>> taskImageKeysCache) {
        List<String> keys = employeeRecordExportImages.collectImageKeys(dto.getImageUrls(), dto.getFileKey());
        if (!keys.isEmpty()) {
            return keys;
        }
        if (row == null || RecordJsonSupport.isBlank(row.getTaskId())) {
            return keys;
        }
        return taskImageKeysCache.computeIfAbsent(row.getTaskId(), taskId -> {
            Task task = taskMapper.selectTaskByTaskId(taskId);
            return task != null ? parseImageUrlList(task) : Collections.emptyList();
        });
    }

    private String[] toEmployeeRecordExportCells(EmployeeRecordDTO dto) {
        return new String[] {
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
                ExcelExportHelper.cell(dto.getSmartMark()),
        };
    }

    private List<String> parseImageUrlList(Task task) {
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

}
