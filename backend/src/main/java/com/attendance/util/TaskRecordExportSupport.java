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
        record.put("ExceptionType", row.getExceptionType());
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

    /**
     * 识别说明：与确认页 recognition notes 顺序对齐
     * 1) 标记类（已删除/未出勤/非「正常」SmartMark/手工补录/人工校准）
     * 2) 班次偏差句
     * 3) 分组摘要（必填缺失 / 看不清 / 格式不规范 / 重名 / 其他）
     */
    public static String formatAnomalyDescription(JSONObject record) {
        if (record == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();

        boolean deleted = Boolean.TRUE.equals(record.getBoolean("isDeleted"))
                || Boolean.TRUE.equals(record.getBoolean("deleted"));
        String smartMark = RecordJsonSupport.pickJson(record, "SmartMark", "Mark", "smartMark");
        boolean absent = smartMark.contains("未出勤");

        if (deleted) {
            lines.add("已删除");
        } else if (absent) {
            lines.add("未出勤");
        } else {
            if (Boolean.TRUE.equals(record.getBoolean("_manuallyAdded"))) {
                lines.add("手工补录");
            }
            for (String part : splitSmartMarkParts(smartMark)) {
                if (part.isEmpty() || "正常".equals(part)) {
                    continue;
                }
                lines.add(part);
            }
            if (containsHandwriting(record, smartMark)
                    && lines.stream().noneMatch(s -> s.contains("手写"))) {
                lines.add("手写");
            }
            if (hasManualCalibration(record)) {
                lines.add("人工校准");
            }
        }

        if (!deleted && !absent) {
            String shiftSentence = ShiftVarianceSupport.formatSentenceZh(record);
            if (!RecordJsonSupport.isBlank(shiftSentence)) {
                lines.add(shiftSentence);
            }
        }

        if (!deleted) {
            appendGroupedAnomalySummaries(record, lines);
        }

        return joinAnomalyLines(lines);
    }

    private static void appendGroupedAnomalySummaries(JSONObject record, List<String> lines) {
        LinkedHashSet<String> required = new LinkedHashSet<>();
        LinkedHashSet<String> unreadable = new LinkedHashSet<>();
        LinkedHashSet<String> format = new LinkedHashSet<>();
        LinkedHashSet<String> duplicate = new LinkedHashSet<>();
        LinkedHashSet<String> other = new LinkedHashSet<>();

        JSONArray anomalies = record.getJSONArray("anomalies");
        if (anomalies != null) {
            for (int i = 0; i < anomalies.size(); i++) {
                String raw = anomalies.getString(i);
                if (RecordJsonSupport.isBlank(raw)) {
                    continue;
                }
                String text = raw.trim();
                if (isMarkRedundantRaw(text)) {
                    continue;
                }
                if (text.startsWith("missing.")) {
                    String field = text.substring("missing.".length());
                    required.add(FIELD_LABELS.getOrDefault(field, field));
                    continue;
                }
                Matcher duplicateMatch = DUPLICATE_PATTERN.matcher(text);
                if (duplicateMatch.matches()) {
                    duplicate.add(duplicateMatch.group(1).trim());
                    continue;
                }
                if ("deleted.record".equals(text)) {
                    continue;
                }
                String formatted = formatAnomalyItem(text);
                if (!RecordJsonSupport.isBlank(formatted) && !isMarkRedundantAnomaly(formatted)) {
                    other.add(formatted);
                }
            }
        }

        for (String field : REQUIRED_EXPORT_FIELDS) {
            if (isBlankField(record, field)) {
                required.add(FIELD_LABELS.getOrDefault(field, field));
            }
        }

        JSONArray unread = record.getJSONArray(RecognizedFieldSanitizer.UNREADABLE_FIELDS_KEY);
        if (unread != null) {
            for (int i = 0; i < unread.size(); i++) {
                String field = unread.getString(i);
                if (!RecordJsonSupport.isBlank(field)) {
                    unreadable.add(FIELD_LABELS.getOrDefault(field, field));
                }
            }
        }

        if (isArrivalDepartureSameTime(record)) {
            format.add("到达与离开时间相同");
        }
        if (Boolean.TRUE.equals(record.getBoolean("_parseMalformed"))) {
            format.add("结构异常");
        }

        appendGroupLine(lines, "必填缺失", required);
        appendGroupLine(lines, "看不清", unreadable);
        appendGroupLine(lines, "格式不规范", format);
        appendGroupLine(lines, "重名", duplicate);
        appendGroupLine(lines, "其他异常", other);
    }

    private static final String[] REQUIRED_EXPORT_FIELDS = {
            "NOM_PRENOM", "Date", "ARRIVEE", "DEPAR", "PAUSE"
    };

    private static void appendGroupLine(List<String> lines, String label, Set<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        lines.add(label + "：" + String.join("、", items));
    }

    private static boolean isBlankField(JSONObject record, String field) {
        String value = RecordJsonSupport.pickJson(record, field);
        return RecordJsonSupport.isBlank(value) || "???".equals(value.trim());
    }

    private static boolean isArrivalDepartureSameTime(JSONObject record) {
        String arrive = RecordJsonSupport.pickJson(record, "ARRIVEE", "arrival");
        String depart = RecordJsonSupport.pickJson(record, "DEPAR", "DEPART", "departure");
        if (RecordJsonSupport.isBlank(arrive) || RecordJsonSupport.isBlank(depart)) {
            return false;
        }
        Integer a = parseTimeToMinutes(arrive);
        Integer b = parseTimeToMinutes(depart);
        return a != null && b != null && a.equals(b);
    }

    private static boolean hasManualCalibration(JSONObject record) {
        if (Boolean.TRUE.equals(record.getBoolean("_manualCalibrated"))) {
            return true;
        }
        Object history = record.get("_calibrationHistory");
        if (history instanceof JSONArray) {
            return !((JSONArray) history).isEmpty();
        }
        if (history instanceof List) {
            return !((List<?>) history).isEmpty();
        }
        if (history instanceof String) {
            String raw = ((String) history).trim();
            return raw.startsWith("[") && raw.length() > 2;
        }
        return false;
    }

    private static List<String> splitSmartMarkParts(String smartMark) {
        List<String> parts = new ArrayList<>();
        if (RecordJsonSupport.isBlank(smartMark)) {
            return parts;
        }
        for (String part : smartMark.split("[;；|,，]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private static boolean isMarkRedundantRaw(String text) {
        return "内容模糊".equals(text) || "手写内容".equals(text) || "未出勤".equals(text)
                || "模糊".equals(text) || "手写".equals(text);
    }

    /** 与确认页识别说明一致：有序编号，一条一行（Excel 单元格内换行） */
    private static String joinAnomalyLines(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ").append(parts.get(i));
        }
        return sb.toString();
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
