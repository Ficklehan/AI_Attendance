package com.attendance.dto;

/**
 * 识别结果图片可读性评估（内容层：行级模糊率 + 关键字段未知率）。
 */
public class ImageQualityAssessment {

    public enum Level {
        OK,
        WARN,
        BLOCK
    }

    /** 内容层拦截原因（i18n key 后缀） */
    public enum BlockReason {
        BLUR_ROWS,
        UNKNOWN_FIELDS,
        FEW_ROWS_UNKNOWN
    }

    public static final String LAYER_IMAGE = "image";
    public static final String LAYER_CONTENT = "content";

    private final Level level;
    private final int blurPercent;
    private final int unknownPercent;
    private final int totalRows;
    private final int effectiveRows;
    private final int blurRowCount;
    private final int blurDenominatorRows;
    private final int unknownCellCount;
    private final int unknownCellTotal;
    private final BlockReason blockReason;
    private final int blockBlurThreshold;
    private final int blockUnknownThreshold;
    private final String blurRateDenominator;
    private final String unknownRateScope;
    private final boolean unknownRateExcludeAbsent;

    public ImageQualityAssessment(Level level, int blurPercent, int unknownPercent, int totalRows, int effectiveRows) {
        this(level, blurPercent, unknownPercent, totalRows, effectiveRows,
                0, 0, 0, 0, null, 0, 0,
                ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS,
                ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS, true);
    }

    public ImageQualityAssessment(Level level, int blurPercent, int unknownPercent,
                                  int totalRows, int effectiveRows,
                                  int blurRowCount, int blurDenominatorRows,
                                  int unknownCellCount, int unknownCellTotal,
                                  BlockReason blockReason,
                                  int blockBlurThreshold, int blockUnknownThreshold,
                                  String blurRateDenominator, String unknownRateScope,
                                  boolean unknownRateExcludeAbsent) {
        this.level = level != null ? level : Level.OK;
        this.blurPercent = Math.max(0, blurPercent);
        this.unknownPercent = Math.max(0, unknownPercent);
        this.totalRows = Math.max(0, totalRows);
        this.effectiveRows = Math.max(0, effectiveRows);
        this.blurRowCount = Math.max(0, blurRowCount);
        this.blurDenominatorRows = Math.max(0, blurDenominatorRows);
        this.unknownCellCount = Math.max(0, unknownCellCount);
        this.unknownCellTotal = Math.max(0, unknownCellTotal);
        this.blockReason = blockReason;
        this.blockBlurThreshold = blockBlurThreshold;
        this.blockUnknownThreshold = blockUnknownThreshold;
        this.blurRateDenominator = blurRateDenominator != null
                ? blurRateDenominator : ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS;
        this.unknownRateScope = unknownRateScope != null
                ? unknownRateScope : ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS;
        this.unknownRateExcludeAbsent = unknownRateExcludeAbsent;
    }

    public static ImageQualityAssessment ok() {
        return new ImageQualityAssessment(Level.OK, 0, 0, 0, 0);
    }

    public Level getLevel() {
        return level;
    }

    public int getBlurPercent() {
        return blurPercent;
    }

    public int getUnknownPercent() {
        return unknownPercent;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getEffectiveRows() {
        return effectiveRows;
    }

    public int getBlurRowCount() {
        return blurRowCount;
    }

    public int getBlurDenominatorRows() {
        return blurDenominatorRows;
    }

    public int getUnknownCellCount() {
        return unknownCellCount;
    }

    public int getUnknownCellTotal() {
        return unknownCellTotal;
    }

    public BlockReason getBlockReason() {
        return blockReason;
    }

    public int getBlockBlurThreshold() {
        return blockBlurThreshold;
    }

    public int getBlockUnknownThreshold() {
        return blockUnknownThreshold;
    }

    public String getBlurRateDenominator() {
        return blurRateDenominator;
    }

    public String getUnknownRateScope() {
        return unknownRateScope;
    }

    public boolean isUnknownRateExcludeAbsent() {
        return unknownRateExcludeAbsent;
    }

    public boolean isBlock() {
        return level == Level.BLOCK;
    }

    public boolean isWarn() {
        return level == Level.WARN;
    }

    public boolean shouldPersistWarning() {
        return level == Level.WARN;
    }
}
