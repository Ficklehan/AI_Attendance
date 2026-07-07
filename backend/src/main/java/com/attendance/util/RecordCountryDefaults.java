package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import com.attendance.config.CountryCatalog;

/**
 * 识别结果 Pays：默认使用当前工作国家，不再保留 AI 识别国别。
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

    /** 将 Pays 设为当前工作国家（有有效工作国家时覆盖 AI 识别值）。 */
    public static void applyWorkingCountryPays(JSONObject record, String workingCountryCode) {
        if (record == null) {
            return;
        }
        String defaultPays = defaultPaysValue(workingCountryCode);
        if (defaultPays != null && !defaultPays.trim().isEmpty()) {
            record.put("Pays", defaultPays);
        }
    }

    /** @deprecated 使用 {@link #applyWorkingCountryPays} */
    public static void applyMissingPays(JSONObject record, String workingCountryCode) {
        applyWorkingCountryPays(record, workingCountryCode);
    }
}
