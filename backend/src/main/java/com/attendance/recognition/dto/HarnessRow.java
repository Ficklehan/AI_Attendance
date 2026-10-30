package com.attendance.recognition.dto;

public class HarnessRow {
    public String rowNo = "";
    public OcrLine no = new OcrLine();
    public OcrLine name = new OcrLine();
    public boolean nameCrossed;
    public boolean nameHasReplacement;
    public OcrLine agency = new OcrLine();
    public OcrLine shift = new OcrLine();
    public OcrLine arrivo = new OcrLine();
    public OcrLine uscita = new OcrLine();
    public OcrLine pausa = new OcrLine();
    public OcrLine signature = new OcrLine();
    public OcrLine observations = new OcrLine();
}
