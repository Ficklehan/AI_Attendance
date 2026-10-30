package com.attendance.recognition.dto;

public class OcrRawResult {
    public int rowIndex;
    public String pageNum = "";
    public String noText = "";
    public String name = "";
    public boolean nameStruck;
    public boolean nameHasReplacement;
    public boolean rowDeleted;
    public CellEvidence arrive = new CellEvidence();
    public CellEvidence depart = new CellEvidence();
    public String pauseText = "";
    public String agency = "";
    public String shift = "";
    public String observations = "";
    public boolean signatureHasInk;
    public String signatureText = "";
    public String headerWarehouse = "";
    public String headerDateRaw = "";
}
