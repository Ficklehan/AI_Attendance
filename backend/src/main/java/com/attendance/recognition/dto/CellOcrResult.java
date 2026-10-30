package com.attendance.recognition.dto;

import java.util.ArrayList;
import java.util.List;

public class CellOcrResult {
    public String text = "";
    public double confidence = 0;
    public boolean lowConfidence;
    public List<String> lines = new ArrayList<String>();

    public static CellOcrResult empty() {
        return new CellOcrResult();
    }

    public String primaryLine() {
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            }
        }
        return text == null ? "" : text.trim();
    }

    public String primaryDigitLine() {
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line != null && line.matches(".*\\d.*")) {
                    return line.trim();
                }
            }
        }
        return primaryLine();
    }
}
