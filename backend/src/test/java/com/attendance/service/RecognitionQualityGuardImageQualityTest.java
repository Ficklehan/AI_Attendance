package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.dto.ImageQualityConfigDTO;
import com.attendance.util.RecognizedFieldSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RecognitionQualityGuardImageQualityTest {

    @Mock
    private RecognitionPromptGuard recognitionPromptGuard;

    @Mock
    private ImageQualityConfigService imageQualityConfigService;

    @Mock
    private ConfirmValidationService confirmValidationService;

    @InjectMocks
    private RecognitionQualityGuard guard;

    private List<JSONObject> records;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(imageQualityConfigService.getConfig())
                .thenReturn(ImageQualityConfigDTO.defaults());
        ConfirmValidationConfigDTO confirmConfig = new ConfirmValidationConfigDTO();
        confirmConfig.setRequiredFields(new ArrayList<>(ConfirmValidationConfigDTO.defaultRequiredFields()));
        org.mockito.Mockito.when(confirmValidationService.getConfig()).thenReturn(confirmConfig);
        records = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            JSONObject row = new JSONObject();
            row.put("NO", String.valueOf(i + 1));
            row.put("NOM_PRENOM", "User " + i);
            row.put("ARRIVEE", "08:00");
            row.put("DEPAR", "17:00");
            row.put("Date", "2026-06-01");
            row.put("PAUSE", 60);
            row.put("SmartMark", "正常");
            row.put("isDeleted", false);
            records.add(row);
        }
    }

    @Test
    void assess_ok_for_clear_records() {
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertEquals(ImageQualityAssessment.Level.OK, assessment.getLevel());
        assertFalse(assessment.isBlock());
        assertFalse(assessment.isWarn());
    }

    @Test
    void assess_block_when_most_effective_rows_low_readability() {
        for (int i = 0; i < 6; i++) {
            records.get(i).put("ARRIVEE", "???");
            records.get(i).put("DEPAR", "???");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertTrue(assessment.isBlock());
        assertEquals(ImageQualityAssessment.BlockReason.BLUR_ROWS, assessment.getBlockReason());
    }

    @Test
    void smartMark_blur_does_not_count_without_required_field_unreadable() {
        for (int i = 0; i < 6; i++) {
            records.get(i).put("SmartMark", "模糊");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertFalse(assessment.isBlock());
        assertEquals(0, assessment.getBlurRowCount());
    }

    @Test
    void non_required_no_unreadable_does_not_count_as_blur_row() {
        ConfirmValidationConfigDTO confirmConfig = new ConfirmValidationConfigDTO();
        confirmConfig.setRequiredFields(Arrays.asList("Date", "ARRIVEE", "DEPAR"));
        org.mockito.Mockito.when(confirmValidationService.getConfig()).thenReturn(confirmConfig);
        for (int i = 0; i < 6; i++) {
            records.get(i).put("NO", "???");
            records.get(i).put("NOM_PRENOM", "???");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertFalse(assessment.isBlock());
        assertEquals(0, assessment.getBlurRowCount());
    }

    @Test
    void assess_block_when_sanitized_unreadable_metadata_present() {
        for (int i = 0; i < 6; i++) {
            JSONObject row = records.get(i);
            row.put("ARRIVEE", "???");
            row.put("DEPAR", "???");
            RecognizedFieldSanitizer.annotateAndSanitizeRecord(row);
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertTrue(assessment.isBlock());
        assertEquals(ImageQualityAssessment.BlockReason.BLUR_ROWS, assessment.getBlockReason());
    }

    @Test
    void assess_unknown_rate_counts_sanitized_unreadable_metadata() {
        for (JSONObject row : records) {
            row.put("ARRIVEE", "???");
            RecognizedFieldSanitizer.annotateAndSanitizeRecord(row);
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertTrue(assessment.getUnknownPercent() > 0);
        assertTrue(assessment.getUnknownCellCount() > 0);
    }

    @Test
    void assess_excludes_absent_rows_from_blur_stats() {
        for (int i = 0; i < 5; i++) {
            records.get(i).put("SmartMark", "未出勤");
            records.get(i).put("ARRIVEE", "");
            records.get(i).put("DEPAR", "");
        }
        for (int i = 5; i < 8; i++) {
            records.get(i).put("ARRIVEE", "???");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertEquals(5, assessment.getBlurDenominatorRows());
        assertEquals(3, assessment.getBlurRowCount());
    }

    @Test
    void assess_blank_fields_do_not_count_as_unreadable() {
        for (JSONObject row : records) {
            row.put("ARRIVEE", "");
            row.put("DEPAR", "");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertEquals(0, assessment.getUnknownPercent());
        assertEquals(0, assessment.getUnknownCellCount());
    }

    @Test
    void assess_unknown_rate_can_include_absent_when_configured() {
        ImageQualityConfigDTO config = ImageQualityConfigDTO.defaults();
        config.setUnknownRateExcludeAbsent(false);
        config.setUnknownRateScope(ImageQualityConfigDTO.DENOMINATOR_ALL_ROWS);
        org.mockito.Mockito.when(imageQualityConfigService.getConfig()).thenReturn(config);
        for (int i = 0; i < 5; i++) {
            records.get(i).put("SmartMark", "未出勤");
            records.get(i).put("ARRIVEE", "");
            records.get(i).put("DEPAR", "");
            records.get(i).put("NOM_PRENOM", "???");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertTrue(assessment.getUnknownCellTotal() > 0);
    }

    @Test
    void assess_unknown_rate_excludes_absent_by_default() {
        for (int i = 0; i < 5; i++) {
            records.get(i).put("SmartMark", "未出勤");
            records.get(i).put("ARRIVEE", "");
            records.get(i).put("DEPAR", "");
            records.get(i).put("NOM_PRENOM", "???");
        }
        ImageQualityAssessment assessment = guard.assessImageReadability(records);
        assertEquals(0, assessment.getUnknownCellCount());
    }
}
