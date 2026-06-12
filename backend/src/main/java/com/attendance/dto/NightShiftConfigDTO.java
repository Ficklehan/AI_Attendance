package com.attendance.dto;

/**
 * 夜班判定规则（由后端根据到离时间与排班列计算，不由大模型输出）。
 */
public class NightShiftConfigDTO {

    /** 到达时间 ≥ 此值视为夜班，如 20:00 */
    private String startTime = "20:00";
    /** 离开时间 &lt; 此值视为夜班，如 06:00 */
    private String endTime = "06:00";
    /** 到离跨午夜（离岗时刻早于到岗）视为夜班 */
    private boolean crossMidnight = true;
    /** 参考 HORAIRES_DU_TRAVAIL 排班时段 */
    private boolean useScheduleColumn = true;

    public static NightShiftConfigDTO defaults() {
        return new NightShiftConfigDTO();
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public boolean isCrossMidnight() {
        return crossMidnight;
    }

    public void setCrossMidnight(boolean crossMidnight) {
        this.crossMidnight = crossMidnight;
    }

    public boolean isUseScheduleColumn() {
        return useScheduleColumn;
    }

    public void setUseScheduleColumn(boolean useScheduleColumn) {
        this.useScheduleColumn = useScheduleColumn;
    }
}
