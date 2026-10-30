package com.attendance.recognition.ocr;

import com.attendance.recognition.dto.CellOcrResult;

import java.nio.file.Path;

public interface OcrClient {
    CellOcrResult ocrCell(Path cropPng) throws Exception;

    boolean doctorOk();

    String doctorMessage();
}
