package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Locale;

/** 考勤记录 JSON 行字段解析与姓名规范化（TaskService / TaskRecordSyncService 共用） */
public final class RecordJsonSupport {

    private RecordJsonSupport() {
    }

    public static String pickJson(JSONObject row, String... keys) {
        if (row == null) {
            return "";
        }
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

    public static String upper(String v) {
        return v == null ? "" : v.toUpperCase(Locale.ROOT);
    }

    public static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    public static String stripSerialSuffix(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s\\d{2}$", "").trim();
    }
}
