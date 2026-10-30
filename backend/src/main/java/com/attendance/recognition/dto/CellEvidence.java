package com.attendance.recognition.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CellEvidence {
    public BBox bbox;
    public boolean hasInk;
    public boolean hasLongLine;
    public boolean inkAfterLineRemoval;
    public String text = "";
    public double confidence = -1;
    public String source = "none";
    public boolean lowConfidence;
    public String cropPath = "";
    public List<String> ocrLines = new ArrayList<String>();
    /** 到/离提取诊断，只进 RecognitionTrace，不参与规则。 */
    public final Map<String, Object> diag = new LinkedHashMap<String, Object>();

    public boolean hasLegalClock() {
        return text != null && text.matches("\\d{2}:\\d{2}");
    }

    public void putDiag(String key, Object value) {
        if (key == null) {
            return;
        }
        diag.put(key, value);
    }
}
