package com.attendance.dto;

/**
 * 图片清晰度检测：上传前锐度预检 + 识别后质量评分阈值。
 */
public class ImageQualityConfigDTO {

    /** 总开关：关闭后不做阻断/警告（上传锐度预检亦关闭） */
    private boolean enabled = true;
    /** 上传前 Laplacian 锐度预检 */
    private boolean preUploadSharpnessEnabled = true;
    /** 锐度方差下限，低于此值拒绝上传 */
    private double minLaplacianVariance = 80.0;

    /** 阻断：模糊行占比阈值（%） */
    private int blockBlurRowPercent = 50;
    /** 阻断：关键字段未知占比阈值（%） */
    private int blockUnknownFieldPercent = 65;
    /** 阻断：有效行数 ≤ 此值且未知率过高时拒绝 */
    private int blockFewRowsMaxEffective = 2;
    private int blockFewRowsUnknownPercent = 80;

    /** 阻断：畸形解析行占比阈值（%），超过则整单识别失败 */
    private int blockMalformedRowPercent = 10;

    /** 警告：模糊行占比阈值（%） */
    private int warnBlurRowPercent = 30;
    /** 警告：关键字段未知占比阈值（%） */
    private int warnUnknownFieldPercent = 40;

    /**
     * 模糊行占比分母：EFFECTIVE_ROWS=仅有效行（工号或姓名至少一项可读），ALL_ROWS=全部非删除行。
     */
    private String blurRateDenominator = "EFFECTIVE_ROWS";

    /**
     * 关键字段未知率统计范围：EFFECTIVE_ROWS=仅有效行上的关键字段，ALL_ROWS=全部非删除行。
     */
    private String unknownRateScope = "EFFECTIVE_ROWS";

    /** 识别后内容层质量检测（产品已关闭：仅保留上传前锐度拒图） */
    private boolean postRecognitionQualityEnabled = false;

    /** 关键字段未知率统计是否排除未出勤行（默认排除）；同时用于模糊行统计分母 */
    private boolean unknownRateExcludeAbsent = true;

    public static final String DENOMINATOR_EFFECTIVE_ROWS = "EFFECTIVE_ROWS";
    public static final String DENOMINATOR_ALL_ROWS = "ALL_ROWS";

    public static ImageQualityConfigDTO defaults() {
        return new ImageQualityConfigDTO();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPreUploadSharpnessEnabled() {
        return preUploadSharpnessEnabled;
    }

    public void setPreUploadSharpnessEnabled(boolean preUploadSharpnessEnabled) {
        this.preUploadSharpnessEnabled = preUploadSharpnessEnabled;
    }

    public double getMinLaplacianVariance() {
        return minLaplacianVariance;
    }

    public void setMinLaplacianVariance(double minLaplacianVariance) {
        this.minLaplacianVariance = minLaplacianVariance;
    }

    public int getBlockBlurRowPercent() {
        return blockBlurRowPercent;
    }

    public void setBlockBlurRowPercent(int blockBlurRowPercent) {
        this.blockBlurRowPercent = blockBlurRowPercent;
    }

    public int getBlockUnknownFieldPercent() {
        return blockUnknownFieldPercent;
    }

    public void setBlockUnknownFieldPercent(int blockUnknownFieldPercent) {
        this.blockUnknownFieldPercent = blockUnknownFieldPercent;
    }

    public int getBlockFewRowsMaxEffective() {
        return blockFewRowsMaxEffective;
    }

    public void setBlockFewRowsMaxEffective(int blockFewRowsMaxEffective) {
        this.blockFewRowsMaxEffective = blockFewRowsMaxEffective;
    }

    public int getBlockFewRowsUnknownPercent() {
        return blockFewRowsUnknownPercent;
    }

    public void setBlockFewRowsUnknownPercent(int blockFewRowsUnknownPercent) {
        this.blockFewRowsUnknownPercent = blockFewRowsUnknownPercent;
    }

    public int getBlockMalformedRowPercent() {
        return blockMalformedRowPercent;
    }

    public void setBlockMalformedRowPercent(int blockMalformedRowPercent) {
        this.blockMalformedRowPercent = blockMalformedRowPercent;
    }

    public int getWarnBlurRowPercent() {
        return warnBlurRowPercent;
    }

    public void setWarnBlurRowPercent(int warnBlurRowPercent) {
        this.warnBlurRowPercent = warnBlurRowPercent;
    }

    public int getWarnUnknownFieldPercent() {
        return warnUnknownFieldPercent;
    }

    public void setWarnUnknownFieldPercent(int warnUnknownFieldPercent) {
        this.warnUnknownFieldPercent = warnUnknownFieldPercent;
    }

    public String getBlurRateDenominator() {
        return blurRateDenominator;
    }

    public void setBlurRateDenominator(String blurRateDenominator) {
        this.blurRateDenominator = blurRateDenominator;
    }

    public String getUnknownRateScope() {
        return unknownRateScope;
    }

    public void setUnknownRateScope(String unknownRateScope) {
        this.unknownRateScope = unknownRateScope;
    }

    public boolean isPostRecognitionQualityEnabled() {
        return postRecognitionQualityEnabled;
    }

    public void setPostRecognitionQualityEnabled(boolean postRecognitionQualityEnabled) {
        this.postRecognitionQualityEnabled = postRecognitionQualityEnabled;
    }

    public boolean isUnknownRateExcludeAbsent() {
        return unknownRateExcludeAbsent;
    }

    public void setUnknownRateExcludeAbsent(boolean unknownRateExcludeAbsent) {
        this.unknownRateExcludeAbsent = unknownRateExcludeAbsent;
    }
}
