package com.attendance.recognition.dto;

import java.util.ArrayList;
import java.util.List;

public class HarnessSheet {
    public String pageNum = "";
    public String warehouse = "";
    public String dateRaw = "";
    public List<HarnessRow> rows = new ArrayList<HarnessRow>();
}
