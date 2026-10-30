package com.attendance.recognition.dto;

import java.util.ArrayList;
import java.util.List;

public class PageLayout {
    public String warehouse = "";
    public String dateRaw = "";
    public String pageNum = "";
    public int previewWidth;
    public int sourceWidth;
    public int sourceHeight;
    public String source = "";
    public int[] gridXs;
    public int[] gridYs;
    public int headerRows;
    public List<GroundedCell> cells = new ArrayList<GroundedCell>();
}
