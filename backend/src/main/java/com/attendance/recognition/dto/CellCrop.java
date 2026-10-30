package com.attendance.recognition.dto;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class CellCrop {
    public int row;
    public String field;
    public BBox bbox;
    public Path pngPath;
    public BufferedImage image;
    public CellOcrResult ocr;

    public CellCrop() {
    }
}
