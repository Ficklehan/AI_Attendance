package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.ImageQualityConfigDTO;
import com.attendance.util.RecognizedRecordShapeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecognitionQualityGuardMalformedTest {

    @InjectMocks
    private RecognitionQualityGuard guard;

    @Mock
    private ImageQualityConfigService imageQualityConfigService;

    @BeforeEach
    void setUp() {
        ImageQualityConfigDTO config = ImageQualityConfigDTO.defaults();
        config.setBlockMalformedRowPercent(10);
        when(imageQualityConfigService.getConfig()).thenReturn(config);
    }

    @Test
    void rejects_when_malformed_ratio_exceeds_threshold() {
        List<JSONObject> records = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            records.add(new JSONObject());
        }
        for (int i = 0; i < 2; i++) {
            JSONObject malformed = new JSONObject();
            RecognizedRecordShapeSupport.markMalformed(malformed, "test");
            records.add(malformed);
        }
        assertTrue(guard.looksTooManyMalformedRecords(records));
    }

    @Test
    void accepts_when_malformed_ratio_below_threshold() {
        List<JSONObject> records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            records.add(new JSONObject());
        }
        JSONObject malformed = new JSONObject();
        RecognizedRecordShapeSupport.markMalformed(malformed, "test");
        records.add(malformed);
        assertFalse(guard.looksTooManyMalformedRecords(records));
    }

    @Test
    void skips_check_when_threshold_zero() {
        ImageQualityConfigDTO config = ImageQualityConfigDTO.defaults();
        config.setBlockMalformedRowPercent(0);
        when(imageQualityConfigService.getConfig()).thenReturn(config);

        List<JSONObject> records = new ArrayList<>();
        JSONObject malformed = new JSONObject();
        RecognizedRecordShapeSupport.markMalformed(malformed, "test");
        records.add(malformed);
        assertFalse(guard.looksTooManyMalformedRecords(records));
    }
}
