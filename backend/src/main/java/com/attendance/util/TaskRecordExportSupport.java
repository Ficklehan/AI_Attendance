package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.entity.TaskRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 任务 JSON 记录导出 Excel 时的派生字段（页码、出勤工时、异常说明） */
public final class TaskRecordExportSupport {

    private static final Map<String, String> FIELD_LABELS = fieldLabels();
    private static final Pattern DUPLICATE_PATTERN = Pattern.compile("^重名疑似[：:]\\s*(.+)$");

    private TaskRecordExportSupport() {
    }

    public static String resolvePageNum(JSONObject record) {
        return RecordJsonSupport.pickJson(record, "PAGE_NUM", "pageNum", "PageNum");
    }

    /** 将 task_records 行转为导出/派生字段计算用的 JSON 视图 */
    public static JSONObject toExportJson(TaskRecord row) {
        if (row == null) {
            return new JSONObject();
        }
        JSONObject record = new JSONObject();
        record.put("NO", row.getLineNo());
        record.put("employeeNo", row.getEmployeeNo());
        record.put("EMPLOYEE_NO", row.getEmployeeNo());
        record.put("NOM_PRENOM", row.getEmpName());
        record.put("Pays", row.getCountry());
        record.put("Entrepot", row.getWarehouse());
        record.put("Date", row.getWorkDate());
        record.put("AGENCE_INTERIMAIRE", row.getAgency());
        record.put("HORAIRES_DU_TRAVAIL", row.getShift());
        record.put("ARRIVEE", row.getArrival());
        record.put("DEPAR", row.getDeparture());
        record.put("PAUSE", row.getPauseMinutes());
        record.put("SIGNATURE", row.getSignature());
        record.put("Observations", row.getObservations());
        record.put("PAGE_NUM", row.getPageNum());
        record.put("SmartMark", row.getSmartMark());
        record.put("isDeleted", row.isDeleted());
        return record;
    }

    public static String formatWorkHours(JSONObject record) {
        if (record == null) {
            return "-";
        }
        if (Boolean.TRUE.equals(record.getBoolean("isDeleted"))) {
            return "-";
        }
        String smartMark = RecordJsonSupport.pickJson(record, "SmartMark", "Mark", "smartMark");
        if (smartMark.contains("未出勤")) {
            return "-";
        }

        String arriveStr = RecordJsonSupport.pickJson(record, "ARRIVEE", "ArriveTime");
        String departStr = RecordJsonSupport.pickJson(record, "DEPAR", "DepartTime");
        if (RecordJsonSupport.isBlank(arriveStr) || RecordJsonSupport.isBlank(departStr)
                || "???".equals(arriveStr) || "???".equals(departStr)) {
            return "-";
        }

        Integer arriveMinutes = parseTimeToMinutes(arriveStr);
        Integer departMinutes = parseTimeToMinutes(departStr);
        if (arriveMinutes == null || departMinutes == null) {
            return "-";
        }

        int totalMinutes = departMinutes - arriveMinutes;
        if (totalMinutes < 0) {
            totalMinutes += 24 * 60;
        }
        int pause = parsePauseToMinutes(record.get("PAUSE"));
        int workMinutes = totalMinutes - pause;
        if (workMinutes < 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", Math.round(workMinutes * 100.0 / 60.0) / 100.0);
    }

    public static String formatAnomalyDescription(JSONObject record) {
        if (record == null) {
            return "";
        }
        Set<String> parts = new LinkedHashSet<>();

        JSONArray anomalies = record.getJSONArray("anomalies");
        if (anomalies != null) {
            for (int i = 0; i < anomalies.size(); i++) {
                String formatted = formatAnomalyItem(anomalies.getString(i));
                if (!RecordJsonSupport.isBlank(formatted) && !isMarkRedundantAnomaly(formatted)) {
                    parts.add(formatted);
                }
            }
        }

        JSONArray unreadable = record.getJSONArray(RecognizedFieldSanitizer.UNREADABLE_FIELDS_KEY);
        if (unreadable != null && !unreadable.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < unreadable.size(); i++) {
                String field = unreadable.getString(i);
                if (!RecordJsonSupport.isBlank(field)) {
                    labels.add(FIELD_LABELS.getOrDefault(field, field));
                }
            }
            if (!labels.isEmpty()) {
                parts.add("看不清：" + String.join("、", labels));
            }
        }

        String smartMark = RecordJsonSupport.pickJson(record, "SmartMark", "Mark", "smartMark");
        if (smartMark.contains("模糊") && parts.stream().noneMatch(s -> s.contains("模糊"))) {
            parts.add("内容模糊");
        }
        if (containsHandwriting(record, smartMark) && parts.stream().noneMatch(s -> s.contains("手写"))) {
            parts.add("手写内容");
        }
        if (smartMark.contains("未出勤") && parts.stream().noneMatch(s -> s.contains("未出勤"))) {
            parts.add("未出勤");
        }

        return String.join("；", parts);
    }

    private static boolean containsHandwriting(JSONObject record, String smartMark) {
        if (smartMark != null && smartMark.contains("手写")) {
            return true;
        }
        return containsHandwritingToken(record.getString("NO"))
                || containsHandwritingToken(record.getString("NOM_PRENOM"));
    }

    private static boolean containsHandwritingToken(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("手写") || lower.contains("handwritten") || lower.contains("manuscrit");
    }

    private static boolean isMarkRedundantAnomaly(String text) {
        return "内容模糊".equals(text) || "手写内容".equals(text) || "未出勤".equals(text);
    }

    private static String formatAnomalyItem(String raw) {
        if (RecordJsonSupport.isBlank(raw)) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("missing.")) {
            String field = text.substring("missing.".length());
            String label = FIELD_LABELS.getOrDefault(field, field);
            return label + "未识别";
        }
        if ("deleted.record".equals(text)) {
            return "记录已删除";
        }
        Matcher duplicate = DUPLICATE_PATTERN.matcher(text);
        if (duplicate.matches()) {
            return "重名疑似：" + duplicate.group(1).trim();
        }
        return text;
    }

    private static Integer parseTimeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "???".equals(timeStr)) {
            return null;
        }
        try {
            String cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':');
            String[] parts = cleanTime.split(":");
            if (parts.length == 2) {
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                return hours * 60 + minutes;
            }
            if (parts.length == 1) {
                double num = Double.parseDouble(parts[0].trim());
                return (int) (Math.floor(num) * 60 + Math.round((num % 1) * 60));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static int parsePauseToMinutes(Object pauseValue) {
        if (pauseValue == null) {
            return 0;
        }
        String raw = pauseValue.toString().trim();
        if (raw.isEmpty() || "???".equals(raw)) {
            return 0;
        }
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replace(',', '.')
                .replaceAll("\\s+", "")
                .replace("minutes", "min")
                .replace("minute", "min")
                .replace("mins", "min")
                .replace("mn", "min");
        try {
            Matcher hourMinute = Pattern.compile("^(\\d+(?:\\.\\d+)?)h(\\d+(?:\\.\\d+)?)?(?:min|m)?$")
                    .matcher(normalized);
            if (hourMinute.matches()) {
                double hours = Double.parseDouble(hourMinute.group(1));
                double minutes = hourMinute.group(2) == null || hourMinute.group(2).isEmpty()
                        ? 0
                        : Double.parseDouble(hourMinute.group(2));
                return (int) Math.round(hours * 60 + minutes);
            }
            Matcher colon = Pattern.compile("^(\\d{1,2}):(\\d{1,2})$").matcher(normalized);
            if (colon.matches()) {
                return Integer.parseInt(colon.group(1)) * 60 + Integer.parseInt(colon.group(2));
            }
            Matcher minute = Pattern.compile("^(\\d+(?:\\.\\d+)?)(?:min|m)?$").matcher(normalized);
            if (minute.matches()) {
                return (int) Math.round(Double.parseDouble(minute.group(1)));
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private static Map<String, String> fieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("NO", "NO");
        labels.put("Pays", "国家");
        labels.put("Entrepot", "仓库");
        labels.put("Date", "日期");
        labels.put("NOM_PRENOM", "姓名");
        labels.put("AGENCE_INTERIMAIRE", "中介机构");
        labels.put("HORAIRES_DU_TRAVAIL", "班次");
        labels.put("ARRIVEE", "到达");
        labels.put("DEPAR", "离开");
        labels.put("PAUSE", "休息");
        labels.put("SIGNATURE", "员工签名");
        labels.put("Observations", "备注");
        labels.put("PAGE_NUM", "页码");
        return labels;
    }
}
