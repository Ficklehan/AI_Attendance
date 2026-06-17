package com.attendance.util;

import com.attendance.config.CountryCatalog;

import java.util.Map;

/**
 * 从记录 Pays 或任务国家解析夜班规则适用的国家代码。
 */
public final class NightShiftCountryResolver {

    private NightShiftCountryResolver() {
    }

    public static String resolve(String paysValue, String taskCountry) {
        String fromPays = CountryCatalog.resolveCountryCodeFromPays(paysValue);
        if (fromPays != null && !"default".equalsIgnoreCase(fromPays)) {
            return CountryResolver.normalize(fromPays);
        }
        if (taskCountry != null && !taskCountry.trim().isEmpty()) {
            return CountryResolver.normalize(taskCountry);
        }
        return "default";
    }

    public static String resolveFromRecord(Map<String, Object> record, String taskCountry) {
        if (record == null) {
            return resolve(null, taskCountry);
        }
        String pays = firstNonBlank(
                stringValue(record.get("Pays")),
                stringValue(record.get("Country")),
                stringValue(record.get("PAYS")));
        return resolve(pays, taskCountry);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
