package com.attendance.recognition.dto;

/**
 * vision_ground 返回的一格。
 */
public class GroundedCell {
    public int row;
    public String field;
    public BBox bbox;

    public GroundedCell() {
    }

    public GroundedCell(int row, String field, BBox bbox) {
        this.row = row;
        this.field = field;
        this.bbox = bbox;
    }
}
