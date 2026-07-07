package com.attendance.dto.internal;

import com.attendance.dto.response.AgencyBillingSummaryDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单次查询加载的中介账单数据集（汇总 + 分块明细索引） */
public class AgencyBillingBundle {

    private final String startDate;
    private final String endDate;
    private final List<String> dayList;
    private final AgencyBillingSummaryDTO summary;
    private final Map<String, List<AgencyBillingRow>> rowsByBlockKey;

    public AgencyBillingBundle(String startDate,
                               String endDate,
                               List<String> dayList,
                               AgencyBillingSummaryDTO summary,
                               Map<String, List<AgencyBillingRow>> rowsByBlockKey) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.dayList = dayList != null ? new ArrayList<>(dayList) : new ArrayList<>();
        this.summary = summary;
        this.rowsByBlockKey = rowsByBlockKey != null ? rowsByBlockKey : new LinkedHashMap<>();
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public List<String> getDayList() {
        return Collections.unmodifiableList(dayList);
    }

    public AgencyBillingSummaryDTO getSummary() {
        return summary;
    }

    public List<AgencyBillingRow> rowsForBlock(String agencyKey, String warehouseKey, String countryKey) {
        return rowsByBlockKey.getOrDefault(blockKey(agencyKey, warehouseKey, countryKey), Collections.emptyList());
    }

    public static String blockKey(String agencyKey, String warehouseKey, String countryKey) {
        return safe(agencyKey) + "\u0001" + safe(warehouseKey) + "\u0001" + safe(countryKey);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
