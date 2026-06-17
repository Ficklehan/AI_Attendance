package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.mapper.PluginConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmValidationServiceTest {

    @Mock
    private PluginConfigMapper pluginConfigMapper;

    @InjectMocks
    private ConfirmValidationService confirmValidationService;

    @BeforeEach
    void setup() {
        when(pluginConfigMapper.selectValue(ConfirmValidationService.CONFIG_KEY)).thenReturn(null);
    }

    @Test
    void rejectsNormalRowMissingConfiguredFields() {
        Map<String, Object> record = validNormalRecord();
        record.remove("ARRIVEE");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void validatesHandwrittenRowWhenFieldsMissing() {
        Map<String, Object> record = validNormalRecord();
        record.put("SmartMark", "手写");
        record.remove("NOM_PRENOM");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void allowsHandwrittenRowWhenFieldsComplete() {
        Map<String, Object> record = validNormalRecord();
        record.put("SmartMark", "手写");
        assertDoesNotThrow(() -> confirmValidationService.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void allowsPauseZero() {
        Map<String, Object> record = validNormalRecord();
        record.put("PAUSE", 0);
        assertDoesNotThrow(() -> confirmValidationService.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void skipsDeletedAndAbsentRows() {
        Map<String, Object> deleted = validNormalRecord();
        deleted.put("isDeleted", true);
        deleted.remove("NOM_PRENOM");

        Map<String, Object> absent = validNormalRecord();
        absent.remove("NOM_PRENOM");
        absent.put("SmartMark", "未出勤");

        assertDoesNotThrow(() -> confirmValidationService.validateConfirmRecords(Arrays.asList(deleted, absent)));
    }

    @Test
    void validatesNormalNightShiftCombo() {
        Map<String, Object> record = validNormalRecord();
        record.put("SmartMark", "正常;夜班");
        record.remove("Date");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record)));
    }

    @Test
    void rejectsPlaceholderValuesAsMissing() {
        Map<String, Object> record1 = validNormalRecord();
        record1.put("NOM_PRENOM", "???");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record1)));

        Map<String, Object> record2 = validNormalRecord();
        record2.put("ARRIVEE", "-");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record2)));

        Map<String, Object> record3 = validNormalRecord();
        record3.put("DEPAR", "n/a");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record3)));
    }

    @Test
    void rejectsSameArrivalDeparture() {
        Map<String, Object> record = validNormalRecord();
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "08:00");
        assertThrows(BusinessException.class, () -> confirmValidationService.validateConfirmRecords(Arrays.asList(record)));
    }

    private static Map<String, Object> validNormalRecord() {
        Map<String, Object> record = new HashMap<>();
        record.put("NO", "001");
        record.put("NOM_PRENOM", "Alice");
        record.put("Date", "2026-05-20");
        record.put("ARRIVEE", "08:00");
        record.put("DEPAR", "17:00");
        record.put("PAUSE", 60);
        record.put("SmartMark", "正常");
        return record;
    }
}
