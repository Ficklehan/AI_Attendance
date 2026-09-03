package com.attendance.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.Task;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务详情页导出：主表列序对齐 TaskEdit；异常类型单元格下拉；
 * 另附「修改前后」sheet：同一条记录只写一行，各字段拆成「修改前 / 修改后」两列。
 */
public final class TaskDetailExcelExporter {

    private static final String[] SNAPSHOT_FIELDS = {
            "Pays", "Entrepot", "Date", "NOM_PRENOM", "AGENCE_INTERIMAIRE",
            "HORAIRES_DU_TRAVAIL", "ARRIVEE", "DEPAR", "PAUSE"
    };

    private static final String[] EXCEPTION_TYPE_CODES = {
            "attendance_ok", "paper_ok_ocr_wrong", "paper_wrong_time"
    };

    private static final int CHANGE_ID_COLUMNS = 2;

    private TaskDetailExcelExporter() {
    }

    public static Path export(Task task, String locale) throws IOException {
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        String data = TaskRecordPayloadResolver.resolvePayload(task);
        if (data == null || data.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.NO_EXPORT_DATA);
        }
        JSONArray records = JSON.parseArray(data);
        if (records == null) {
            records = new JSONArray();
        }
        return exportRecords(records, locale);
    }

    public static Path exportRecords(JSONArray records, String locale) throws IOException {
        if (records == null) {
            records = new JSONArray();
        }
        String resolvedLocale = ExportLocaleSupport.resolveLocale(locale);

        Path tempFile = Files.createTempFile("attendance-export-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = ExcelExportStyles.createHeaderStyle(workbook);
            CellStyle bodyStyle = ExcelExportStyles.createBodyStyle(workbook);

            writeAttendanceSheet(workbook, records, resolvedLocale, headerStyle, bodyStyle);
            CellStyle changedStyle = ExcelExportStyles.createChangedCellStyle(workbook);
            writeChangesSheet(workbook, records, resolvedLocale, headerStyle, bodyStyle, changedStyle);

            try (OutputStream out = Files.newOutputStream(tempFile)) {
                workbook.write(out);
            }
            return tempFile;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // ignore
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.SYSTEM_ERROR);
        }
    }

    private static void writeAttendanceSheet(XSSFWorkbook workbook, JSONArray records, String locale,
                                      CellStyle headerStyle, CellStyle bodyStyle) {
        String sheetName = ExportLocaleSupport.text(locale, "sheet.taskDetail");
        Sheet sheet = workbook.createSheet(sheetName != null && !sheetName.isEmpty() ? sheetName : "Attendance");
        String[] headers = ExportLocaleSupport.headers(locale, "taskJson.headers");
        writeHeaderRow(sheet, headers, headerStyle);

        String[] exceptionLabels = exceptionTypeLabels(locale);
        int exceptionCol = headers.length - 1;
        int dataRows = 0;
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (record == null) {
                continue;
            }
            writeBodyRow(sheet, buildAttendanceRow(record, locale, exceptionLabels), bodyStyle);
            dataRows++;
        }

        if (dataRows > 0) {
            addDropdown(sheet, exceptionCol, 1, dataRows, exceptionLabels);
        }
        ExcelExportStyles.fitColumns(sheet, headers.length, 6, 48);
        sheet.createFreezePane(0, 1);
    }

    private static void writeChangesSheet(XSSFWorkbook workbook, JSONArray records, String locale,
                                   CellStyle headerStyle, CellStyle bodyStyle, CellStyle changedStyle) {
        String sheetName = ExportLocaleSupport.text(locale, "sheet.taskChanges");
        Sheet sheet = workbook.createSheet(sheetName != null && !sheetName.isEmpty() ? sheetName : "Changes");
        String[] sourceHeaders = ExportLocaleSupport.headers(locale, "taskJson.headers");
        String beforeLabel = ExportLocaleSupport.text(locale, "taskChanges.before");
        String afterLabel = ExportLocaleSupport.text(locale, "taskChanges.after");
        if (beforeLabel == null || beforeLabel.isEmpty()) {
            beforeLabel = "修改前";
        }
        if (afterLabel == null || afterLabel.isEmpty()) {
            afterLabel = "修改后";
        }
        String[] headers = buildChangeHeaders(sourceHeaders, beforeLabel, afterLabel);
        writeHeaderRow(sheet, headers, headerStyle);

        String[] exceptionLabels = exceptionTypeLabels(locale);
        int fieldCount = Math.max(0, sourceHeaders.length - CHANGE_ID_COLUMNS);
        int written = 0;
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (record == null) {
                continue;
            }
            JSONObject baseline = record.getJSONObject("_aiBaseline");
            if (baseline == null || baseline.isEmpty() || !hasFieldChange(record, baseline)) {
                continue;
            }
            String[] before = buildAttendanceRow(mergeBaselineRow(record, baseline), locale, exceptionLabels);
            String[] after = buildAttendanceRow(record, locale, exceptionLabels);
            String[] merged = new String[headers.length];
            boolean[] changed = new boolean[headers.length];
            merged[0] = after.length > 0 ? after[0] : "";
            merged[1] = after.length > 1 ? after[1] : "";
            for (int f = 0; f < fieldCount; f++) {
                int source = CHANGE_ID_COLUMNS + f;
                String from = source < before.length && before[source] != null ? before[source] : "";
                String to = source < after.length && after[source] != null ? after[source] : "";
                int beforeCol = CHANGE_ID_COLUMNS + f * 2;
                int afterCol = beforeCol + 1;
                merged[beforeCol] = from;
                merged[afterCol] = to;
                if (!from.equals(to)) {
                    changed[beforeCol] = true;
                    changed[afterCol] = true;
                }
            }
            writeBodyRow(sheet, merged, bodyStyle, changedStyle, changed);
            written++;
        }
        if (written == 0) {
            String empty = ExportLocaleSupport.text(locale, "taskChanges.empty");
            String[] emptyRow = new String[headers.length];
            emptyRow[0] = empty != null ? empty : "";
            for (int c = 1; c < emptyRow.length; c++) {
                emptyRow[c] = "";
            }
            writeBodyRow(sheet, emptyRow, bodyStyle);
        }
        ExcelExportStyles.fitColumns(sheet, headers.length, 8, 36);
        sheet.createFreezePane(CHANGE_ID_COLUMNS, 1);
    }

    static String[] buildChangeHeaders(String[] sourceHeaders, String beforeLabel, String afterLabel) {
        if (sourceHeaders == null || sourceHeaders.length == 0) {
            return new String[0];
        }
        int idCount = Math.min(CHANGE_ID_COLUMNS, sourceHeaders.length);
        int fieldCount = Math.max(0, sourceHeaders.length - idCount);
        String[] headers = new String[idCount + fieldCount * 2];
        for (int i = 0; i < idCount; i++) {
            headers[i] = sourceHeaders[i] != null ? sourceHeaders[i] : "";
        }
        for (int f = 0; f < fieldCount; f++) {
            String name = sourceHeaders[idCount + f] != null ? sourceHeaders[idCount + f] : "";
            headers[idCount + f * 2] = pairHeader(name, beforeLabel);
            headers[idCount + f * 2 + 1] = pairHeader(name, afterLabel);
        }
        return headers;
    }

    static String pairHeader(String fieldName, String sideLabel) {
        String name = fieldName != null ? fieldName.trim() : "";
        String side = sideLabel != null ? sideLabel.trim() : "";
        if (name.isEmpty()) {
            return side;
        }
        if (side.isEmpty()) {
            return name;
        }
        return name + "（" + side + "）";
    }

    private static boolean hasFieldChange(JSONObject record, JSONObject baseline) {
        for (String field : SNAPSHOT_FIELDS) {
            if (!normalizeValue(baseline.get(field)).equals(normalizeValue(record.get(field)))) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject mergeBaselineRow(JSONObject record, JSONObject baseline) {
        JSONObject before = new JSONObject(true);
        before.putAll(record);
        for (String field : SNAPSHOT_FIELDS) {
            if (baseline.containsKey(field)) {
                before.put(field, baseline.get(field));
            }
        }
        return before;
    }

    private static String[] buildAttendanceRow(JSONObject record, String locale, String[] exceptionLabels) {
        return new String[]{
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
                ExcelExportHelper.cell(record.get("PAUSE")),
                ExcelExportHelper.cell(TaskRecordExportSupport.formatWorkHours(record)),
                ExcelExportHelper.cell(record.getString("SIGNATURE")),
                ExcelExportHelper.cell(record.getString("Observations")),
                ExcelExportHelper.cell(TaskRecordExportSupport.formatAnomalyDescription(record)),
                ExcelExportHelper.cell(formatExceptionType(record, locale, exceptionLabels))
        };
    }

    private static void writeHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(26f);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i] != null ? headers[i] : "");
            cell.setCellStyle(style);
        }
    }

    private static void writeBodyRow(Sheet sheet, String[] values, CellStyle style) {
        writeBodyRow(sheet, values, style, style, null);
    }

    private static void writeBodyRow(Sheet sheet, String[] values, CellStyle bodyStyle,
                                     CellStyle changedStyle, boolean[] changed) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        int maxLines = 1;
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            int lines = 1;
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '\n') {
                    lines++;
                }
            }
            if (lines > maxLines) {
                maxLines = lines;
            }
        }
        row.setHeightInPoints(Math.min(18f * maxLines, 120f));
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i] != null ? values[i] : "");
            boolean isChanged = changed != null && i < changed.length && changed[i];
            cell.setCellStyle(isChanged ? changedStyle : bodyStyle);
        }
    }

    private static void addDropdown(Sheet sheet, int colIndex, int firstDataRow, int lastDataRow, String[] options) {
        if (options == null || options.length == 0 || lastDataRow < firstDataRow) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        CellRangeAddressList addressList = new CellRangeAddressList(firstDataRow, lastDataRow, colIndex, colIndex);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setSuppressDropDownArrow(false);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private static String[] exceptionTypeLabels(String locale) {
        List<String> labels = new ArrayList<>();
        for (String code : EXCEPTION_TYPE_CODES) {
            labels.add(ExportLocaleSupport.text(locale, "exceptionType." + code));
        }
        return labels.toArray(new String[0]);
    }

    private static String formatExceptionType(JSONObject record, String locale, String[] labels) {
        String code = record.getString("ExceptionType");
        if (code == null || code.trim().isEmpty()) {
            return "";
        }
        for (int i = 0; i < EXCEPTION_TYPE_CODES.length; i++) {
            if (EXCEPTION_TYPE_CODES[i].equals(code.trim())) {
                return labels[i];
            }
        }
        return code;
    }

    private static String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

}
