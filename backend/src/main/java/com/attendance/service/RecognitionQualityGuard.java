package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.dto.ImageQualityConfigDTO;
import com.attendance.util.RecognizedRecordShapeSupport;
import com.attendance.util.RowReadabilitySupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 识别结果质量守卫：提示词示例照抄、看不清却臆测时间等可明确判定的情形。
 * 不再用连号工号/同一中介/统一休息/模板法语姓/班次轮换等业务常态做「编造」打分。
 */
@Component
public class RecognitionQualityGuard {

    @Autowired
    private ImageQualityConfigService imageQualityConfigService;

    @Autowired
    private ConfirmValidationService confirmValidationService;

    /**
     * 识别后内容层评估：低可读行占比 + 必填字段未知率（字段来自确认任务必填校验配置）。
     */
    public ImageQualityAssessment assessImageReadability(List<JSONObject> records) {
        ImageQualityConfigDTO config = imageQualityConfigService.getConfig();
        if (config == null || !config.isEnabled() || !config.isPostRecognitionQualityEnabled()) {
            return ImageQualityAssessment.ok();
        }
        if (records == null || records.isEmpty()) {
            return ImageQualityAssessment.ok();
        }

        List<String> qualityFields = RowReadabilitySupport.safeRequiredFields(
                confirmValidationService.getConfig().getRequiredFields());

        boolean blurDenomEffective = ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS
                .equalsIgnoreCase(config.getBlurRateDenominator());
        boolean unknownScopeEffective = ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS
                .equalsIgnoreCase(config.getUnknownRateScope());
        boolean excludeAbsentFromStats = config.isUnknownRateExcludeAbsent();

        int totalRows = 0;
        int effectiveRows = 0;
        int blurRows = 0;
        int blurDenominatorRows = 0;
        int unknownCells = 0;
        int unknownCellTotal = 0;

        for (JSONObject record : records) {
            if (RowReadabilitySupport.isDeletedRow(record)) {
                continue;
            }
            totalRows++;
            boolean effective = RowReadabilitySupport.isEffectiveRow(record);
            if (effective) {
                effectiveRows++;
            }
            boolean absent = RowReadabilitySupport.isAbsentRow(record);
            boolean skipAbsent = excludeAbsentFromStats && absent;

            boolean countsForBlurDenom = blurDenomEffective ? effective : true;
            if (countsForBlurDenom && !skipAbsent) {
                blurDenominatorRows++;
                if (RowReadabilitySupport.isLowReadabilityRow(record, qualityFields)) {
                    blurRows++;
                }
            }

            boolean countsForUnknown = unknownScopeEffective ? effective : true;
            if (countsForUnknown && !skipAbsent) {
                for (String field : qualityFields) {
                    if (RowReadabilitySupport.isFieldUnreadable(record, field)) {
                        unknownCellTotal++;
                        unknownCells++;
                        continue;
                    }
                    String cell = RowReadabilitySupport.getFieldValue(record, field);
                    if (RowReadabilitySupport.isBlankField(cell)) {
                        continue;
                    }
                    unknownCellTotal++;
                    if (RowReadabilitySupport.isExplicitlyUnreadableField(cell)) {
                        unknownCells++;
                    }
                }
            }
        }

        if (totalRows == 0) {
            return ImageQualityAssessment.ok();
        }

        // 无有效行时回退为按全部非删除行统计，避免空表/全 ??? 被误判为通过
        if (effectiveRows == 0) {
            blurDenomEffective = false;
            unknownScopeEffective = false;
            blurDenominatorRows = 0;
            blurRows = 0;
            unknownCells = 0;
            unknownCellTotal = 0;
            for (JSONObject record : records) {
                if (RowReadabilitySupport.isDeletedRow(record)) {
                    continue;
                }
                boolean absent = RowReadabilitySupport.isAbsentRow(record);
                boolean skipAbsent = excludeAbsentFromStats && absent;
                if (!skipAbsent) {
                    blurDenominatorRows++;
                    if (RowReadabilitySupport.isLowReadabilityRow(record, qualityFields)) {
                        blurRows++;
                    }
                }
                if (!skipAbsent) {
                    for (String field : qualityFields) {
                        if (RowReadabilitySupport.isFieldUnreadable(record, field)) {
                            unknownCellTotal++;
                            unknownCells++;
                            continue;
                        }
                        String cell = RowReadabilitySupport.getFieldValue(record, field);
                        if (RowReadabilitySupport.isBlankField(cell)) {
                            continue;
                        }
                        unknownCellTotal++;
                        if (RowReadabilitySupport.isExplicitlyUnreadableField(cell)) {
                            unknownCells++;
                        }
                    }
                }
            }
        }

        if (blurDenominatorRows == 0) {
            blurDenominatorRows = Math.max(1, totalRows);
        }

        double blurRate = (double) blurRows / blurDenominatorRows;
        double unknownRate = unknownCellTotal > 0 ? (double) unknownCells / unknownCellTotal : 0.0;
        int blurPercent = (int) Math.round(blurRate * 100);
        int unknownPercent = (int) Math.round(unknownRate * 100);

        int blockBlur = config.getBlockBlurRowPercent();
        int blockUnknown = config.getBlockUnknownFieldPercent();
        double blockBlurRate = blockBlur / 100.0;
        double blockUnknownRate = blockUnknown / 100.0;
        double blockFewUnknownRate = config.getBlockFewRowsUnknownPercent() / 100.0;

        ImageQualityAssessment.Level level = ImageQualityAssessment.Level.OK;
        ImageQualityAssessment.BlockReason blockReason = null;

        if (blurRate >= blockBlurRate) {
            level = ImageQualityAssessment.Level.BLOCK;
            blockReason = ImageQualityAssessment.BlockReason.BLUR_ROWS;
        } else if (unknownRate >= blockUnknownRate) {
            level = ImageQualityAssessment.Level.BLOCK;
            blockReason = ImageQualityAssessment.BlockReason.UNKNOWN_FIELDS;
        } else if (effectiveRows <= config.getBlockFewRowsMaxEffective()
                && unknownRate >= blockFewUnknownRate) {
            level = ImageQualityAssessment.Level.BLOCK;
            blockReason = ImageQualityAssessment.BlockReason.FEW_ROWS_UNKNOWN;
        }

        return new ImageQualityAssessment(
                level, blurPercent, unknownPercent, totalRows, effectiveRows,
                blurRows, blurDenominatorRows, unknownCells, unknownCellTotal,
                blockReason, blockBlur, blockUnknown,
                config.getBlurRateDenominator(), config.getUnknownRateScope(),
                config.isUnknownRateExcludeAbsent());
    }

    public boolean isTooBlurryToAccept(List<JSONObject> records) {
        return assessImageReadability(records).isBlock();
    }

    public Map<String, Object> blurryBlockMessageArgs(ImageQualityAssessment assessment) {
        Map<String, Object> args = new HashMap<>();
        if (assessment == null) {
            return args;
        }
        args.put("layer", ImageQualityAssessment.LAYER_CONTENT);
        args.put("blurPercent", assessment.getBlurPercent());
        args.put("unknownPercent", assessment.getUnknownPercent());
        args.put("blockBlurThreshold", assessment.getBlockBlurThreshold());
        args.put("blockUnknownThreshold", assessment.getBlockUnknownThreshold());
        args.put("blurRowCount", assessment.getBlurRowCount());
        args.put("blurDenominatorRows", assessment.getBlurDenominatorRows());
        args.put("effectiveRows", assessment.getEffectiveRows());
        args.put("totalRows", assessment.getTotalRows());
        args.put("blurRateDenominator", assessment.getBlurRateDenominator());
        args.put("unknownRateScope", assessment.getUnknownRateScope());
        args.put("unknownRateExcludeAbsent", assessment.isUnknownRateExcludeAbsent());
        if (assessment.getBlockReason() != null) {
            args.put("blockReason", assessment.getBlockReason().name());
        }
        return args;
    }

    private static boolean isUnknown(String value) {
        return RowReadabilitySupport.isUnknown(value);
    }

    private static boolean hasFilledTime(JSONObject r) {
        return !isUnknown(safe(r.getString("ARRIVEE"))) || !isUnknown(safe(r.getString("DEPAR")));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
