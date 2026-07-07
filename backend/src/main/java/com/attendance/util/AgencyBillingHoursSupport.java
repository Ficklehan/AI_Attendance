package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.internal.AgencyBillingRow;
import com.attendance.dto.response.AgencyBillingDetailDTO;
import com.attendance.dto.response.AgencyBillingSummaryDTO;

/**
 * 中介账单行级工时解析（复用考勤导出逻辑，避免 JSON 全量转换）。
 */
public final class AgencyBillingHoursSupport {

    private AgencyBillingHoursSupport() {
    }

    public static boolean isBillable(AgencyBillingRow row) {
        if (row == null) {
            return false;
        }
        return !RowReadabilitySupport.isAbsentRow(row.toReadabilityJson());
    }

    public static Double parseWorkHours(AgencyBillingRow row) {
        if (row == null) {
            return null;
        }
        JSONObject json = row.toHoursJson();
        String hours = TaskRecordExportSupport.formatWorkHours(json);
        if ("-".equals(hours) || RecordJsonSupport.isBlank(hours)) {
            return null;
        }
        try {
            return Double.parseDouble(hours);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
