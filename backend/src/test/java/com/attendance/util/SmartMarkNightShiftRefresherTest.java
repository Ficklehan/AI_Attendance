package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.NightShiftConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SmartMarkNightShiftRefresherTest {

    private static NightShiftConfigDTO rules() {
        return NightShiftConfigDTO.defaults();
    }

    @Test
    void addsNightShiftWhenTimesQualify() {
        JSONObject record = new JSONObject();
        record.put("ARRIVEE", "22:00");
        record.put("DEPAR", "07:30");
        assertEquals("正常;夜班", SmartMarkNightShiftRefresher.refresh("正常", record, rules()));
    }

    @Test
    void removesNightShiftWhenTimesNoLongerQualify() {
        JSONObject record = new JSONObject();
        record.put("ARRIVEE", "09:00");
        record.put("DEPAR", "17:00");
        assertEquals("正常", SmartMarkNightShiftRefresher.refresh("正常;夜班", record, rules()));
    }

    @Test
    void removesNightShiftWhenDayTimesOverrideSchedule() {
        JSONObject record = new JSONObject();
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "17:00");
        record.put("HORAIRES_DU_TRAVAIL", "22:00-07:00");
        assertEquals("正常", SmartMarkNightShiftRefresher.refresh("正常;夜班", record, rules()));
    }

    @Test
    void absentRecordStripsNightShift() {
        JSONObject record = new JSONObject();
        record.put("ARRIVEE", "");
        record.put("DEPAR", "");
        assertEquals("未出勤", SmartMarkNightShiftRefresher.refresh("未出勤;夜班", record, rules()));
    }
}
