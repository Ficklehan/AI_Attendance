package com.attendance.recognition.dto;

/**
 * Harness 一格：文本 + 置信度 + 原图 bbox。
 */
public class OcrLine {
    public String text = "";
    public double confidence = -1;
    public BBox bbox;
    /** null = 由文本推断；true = 确认空白/仅印刷冒号。 */
    public Boolean empty;

    public boolean isBlank() {
        if (empty != null && empty.booleanValue()) {
            return true;
        }
        return text == null || text.trim().isEmpty();
    }
}
