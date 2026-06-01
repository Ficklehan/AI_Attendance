package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import com.attendance.config.CountryCatalog;

/**
 * 识别结果字段缺省值：Pays 未识别时回退为当前工作国家。
 */
public final class RecordCountryDefaults {

    private RecordCountryDefaults() {
    }

    public static boolean isMissingPays(String pays) {
        if (pays == null) {
            return true;
        }
        String trimmed = pays.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed) || "—".equals(trimmed)) {
            return true;
        }
        if ("???".equals(trimmed) || "??".equals(trimmed)) {
            return true;
        }
        return "illegible".equalsIgnoreCase(trimmed)
                || "n/a".equalsIgnoreCase(trimmed)
                || "null".equalsIgnoreCase(trimmed);
    }

    public static String defaultPaysValue(String workingCountryCode) {
        return CountryCatalog.defaultPaysLabel(workingCountryCode);
    }

    public static void applyMissingPays(JSONObject record, String workingCountryCode) {
        if (record == null || !isMissingPays(record.getString("Pays"))) {
            return;
        }
        String defaultPays = defaultPaysValue(workingCountryCode);
        if (defaultPays != null && !defaultPays.isBlank()) {
            record.put("Pays", defaultPays);
        }
    }
}
