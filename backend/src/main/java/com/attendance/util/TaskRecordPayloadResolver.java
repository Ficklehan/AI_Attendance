package com.attendance.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.entity.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析任务应展示/导出的 JSON 记录载荷：确认后合并 raw_data 与 confirmed_data，
 * 保留全部行且以 confirmed_data 覆盖用户确认前的编辑。
 */
public final class TaskRecordPayloadResolver {

    private TaskRecordPayloadResolver() {
    }

    public static String resolvePayload(Task task) {
        if (task == null) {
            return null;
        }
        String raw = task.getRawData();
        String confirmed = task.getConfirmedData();
        if (!"confirmed".equalsIgnoreCase(task.getStatus())) {
            return firstNonBlank(raw, confirmed);
        }
        if (RecordJsonSupport.isBlank(confirmed)) {
            return raw;
        }
        if (RecordJsonSupport.isBlank(raw)) {
            return confirmed;
        }
        try {
            JSONArray rawArr = JSON.parseArray(raw);
            JSONArray confArr = JSON.parseArray(confirmed);
            return JSON.toJSONString(mergeArrays(rawArr, confArr));
        } catch (Exception ignored) {
            return confirmed;
        }
    }

    static JSONArray mergeArrays(JSONArray rawArr, JSONArray confArr) {
        List<JSONObject> raw = toObjectList(rawArr);
        List<JSONObject> conf = toObjectList(confArr);
        if (conf.isEmpty()) {
            return toJsonArray(raw);
        }
        if (raw.isEmpty()) {
            return toJsonArray(conf);
        }

        boolean hasStableKeys = hasRowKey(raw) || hasRowKey(conf);
        if (!hasStableKeys && raw.size() == conf.size()) {
            List<JSONObject> byIndex = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                byIndex.add(mergeObjects(raw.get(i), conf.get(i)));
            }
            return toJsonArray(byIndex);
        }

        Map<String, JSONObject> confirmedByKey = new LinkedHashMap<>();
        for (int i = 0; i < conf.size(); i++) {
            confirmedByKey.put(rowKey(conf.get(i), i), conf.get(i));
        }

        Set<String> usedConfirmed = new LinkedHashSet<>();
        List<JSONObject> merged = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++) {
            JSONObject rawRow = raw.get(i);
            String key = rowKey(rawRow, i);
            JSONObject confirmedRow = confirmedByKey.get(key);
            if (confirmedRow != null) {
                merged.add(mergeObjects(rawRow, confirmedRow));
                usedConfirmed.add(key);
            } else {
                merged.add(rawRow);
            }
        }

        for (int i = 0; i < conf.size(); i++) {
            String key = rowKey(conf.get(i), i);
            if (!usedConfirmed.contains(key)) {
                merged.add(conf.get(i));
            }
        }

        return toJsonArray(merged);
    }

    private static JSONObject mergeObjects(JSONObject base, JSONObject overlay) {
        if (base == null) {
            return overlay;
        }
        if (overlay == null) {
            return base;
        }
        JSONObject merged = new JSONObject(base);
        merged.putAll(overlay);
        return merged;
    }

    private static boolean hasRowKey(List<JSONObject> rows) {
        for (JSONObject row : rows) {
            if (row != null && row.containsKey("_rowKey") && row.getString("_rowKey") != null
                    && !row.getString("_rowKey").trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String rowKey(JSONObject row, int index) {
        if (row != null && row.containsKey("_rowKey")) {
            String key = row.getString("_rowKey");
            if (key != null && !key.trim().isEmpty()) {
                return key.trim();
            }
        }
        return "__idx_" + index;
    }

    private static List<JSONObject> toObjectList(JSONArray arr) {
        List<JSONObject> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject row = arr.getJSONObject(i);
            if (row != null) {
                list.add(row);
            }
        }
        return list;
    }

    private static JSONArray toJsonArray(List<JSONObject> rows) {
        JSONArray arr = new JSONArray();
        arr.addAll(rows);
        return arr;
    }

    private static String firstNonBlank(String a, String b) {
        if (!RecordJsonSupport.isBlank(a)) {
            return a;
        }
        if (!RecordJsonSupport.isBlank(b)) {
            return b;
        }
        return null;
    }
}
