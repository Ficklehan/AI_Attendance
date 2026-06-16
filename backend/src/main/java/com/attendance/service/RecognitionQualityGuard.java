package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.dto.ImageQualityConfigDTO;
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

    private static final int MIN_EXAMPLE_ROWS_TO_REJECT = 3;

    private static final Pattern EXAMPLE_JSON_LINE = Pattern.compile(
            "^\\s*\\[\"[^\"]+\"(?:,\"[^\"]*\")+\\]\\s*$");

    @Autowired
    private RecognitionPromptGuard recognitionPromptGuard;

    @Autowired
    private ImageQualityConfigService imageQualityConfigService;

    @Autowired
    private ConfirmValidationService confirmValidationService;

    /**
     * 发给 API 前去掉「示例」下的 JSON 行，仅保留规则；避免模型照抄或仿写示例结构。
     */
    public String preparePromptWithoutExamples(String promptFromConfig) {
        if (promptFromConfig == null || promptFromConfig.trim().isEmpty()) {
            return promptFromConfig;
        }
        StringBuilder out = new StringBuilder();
        boolean inExampleSection = false;
        for (String line : promptFromConfig.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.contains("示例") && !trimmed.startsWith("[")) {
                inExampleSection = true;
                out.append(line).append('\n');
                out.append("（输出时：禁止输出本段示例中的任何姓名、中介、时间；仅按图片单元格填写；看不清用 ??? 或 \"\"，严禁编造或猜测补全）\n");
                continue;
            }
            if (inExampleSection) {
                if (EXAMPLE_JSON_LINE.matcher(trimmed).matches()) {
                    continue;
                }
                if (trimmed.isEmpty()) {
                    inExampleSection = false;
                    out.append(line).append('\n');
                }
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    /**
     * 是否照抄了提示词中的示例行（张三/李四/王五等），解析阶段已过滤，此处作双保险。
     */
    public boolean looksFabricated(List<JSONObject> records) {
        if (records == null || records.size() < MIN_EXAMPLE_ROWS_TO_REJECT) {
            return false;
        }
        int exampleHits = 0;
        for (JSONObject record : records) {
            if (recognitionPromptGuard.isPromptExampleRecord(record)) {
                exampleHits++;
            }
        }
        return exampleHits >= MIN_EXAMPLE_ROWS_TO_REJECT;
    }

    public String describeFabricationReason(List<JSONObject> records) {
        return "识别结果含多条提示词示例行（非图中真实数据），请换更清晰、完整的考勤表照片重试。";
    }

    /**
     * 工号/姓名大量 ??? 或 ILLEGIBLE，但到达/离开却整齐填写 → 模型在猜时间而非读图。
     */
    public boolean looksUnreadableWithGuessedTimes(List<JSONObject> records) {
        if (records == null || records.size() < 3) {
            return false;
        }
        int n = records.size();
        int unknownIdentity = 0;
        int unknownNo = 0;
        int unknownName = 0;
        int filledTimes = 0;
        for (JSONObject r : records) {
            String no = safe(r.getString("NO"));
            String name = safe(r.getString("NOM_PRENOM"));
            boolean uNo = isUnknown(no);
            boolean uName = isUnknown(name);
            if (uNo) {
                unknownNo++;
            }
            if (uName) {
                unknownName++;
            }
            if (uNo && uName) {
                unknownIdentity++;
            }
            if (hasFilledTime(r)) {
                filledTimes++;
            }
        }
        if (unknownIdentity >= n * 0.6 && filledTimes >= n * 0.5) {
            return true;
        }
        return unknownNo >= n * 0.7 && unknownName >= n * 0.5 && filledTimes >= n * 0.6;
    }

    public String describeUnreadableReason(List<JSONObject> records) {
        return "表格大量工号/姓名为 ??? 或 ILLEGIBLE，但到达/离开时间却被整齐填写，疑似模型臆测而非读图。请换更清晰照片重试。";
    }

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
