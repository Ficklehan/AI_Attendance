package com.attendance.recognition.harness;

import com.attendance.recognition.dto.CellEvidence;
import com.attendance.recognition.dto.HarnessRow;
import com.attendance.recognition.dto.HarnessSheet;
import com.attendance.recognition.dto.OcrLine;
import com.attendance.recognition.dto.OcrRawResult;

/**
 * Harness 行契约 → 规则引擎 OcrRawResult。不判定 Mark。
 */
public final class HarnessSheetMapper {

    private HarnessSheetMapper() {
    }

    public static OcrRawResult toRaw(HarnessRow row, HarnessSheet sheet, double acceptThreshold) {
        OcrRawResult raw = new OcrRawResult();
        if (row == null) {
            return raw;
        }
        raw.noText = text(row.no);
        if (raw.noText.isEmpty()) {
            raw.noText = row.rowNo == null ? "" : row.rowNo;
        }
        raw.name = text(row.name);
        raw.nameStruck = row.nameCrossed;
        raw.nameHasReplacement = row.nameHasReplacement;
        raw.agency = text(row.agency);
        raw.shift = text(row.shift);
        raw.pauseText = text(row.pausa);
        raw.observations = text(row.observations);
        raw.signatureText = text(row.signature);
        raw.signatureHasInk = !blank(row.signature);
        if (sheet != null) {
            raw.pageNum = sheet.pageNum == null ? "" : sheet.pageNum;
            raw.headerWarehouse = sheet.warehouse == null ? "" : sheet.warehouse;
            raw.headerDateRaw = sheet.dateRaw == null ? "" : sheet.dateRaw;
        }
        fillClock(raw.arrive, row.arrivo, acceptThreshold);
        fillClock(raw.depart, row.uscita, acceptThreshold);
        try {
            raw.rowIndex = Integer.parseInt(raw.noText.trim());
        } catch (Exception e) {
            raw.rowIndex = 0;
        }
        return raw;
    }

    static void fillClock(CellEvidence ev, OcrLine line, double acceptThreshold) {
        if (ev == null) {
            return;
        }
        if (line == null) {
            ev.source = "harness";
            ev.hasInk = false;
            ev.inkAfterLineRemoval = false;
            ev.text = "";
            return;
        }
        ev.source = "harness";
        ev.text = line.text == null ? "" : line.text.trim();
        ev.confidence = line.confidence;
        ev.bbox = line.bbox;
        boolean blank = blank(line);
        ev.hasInk = !blank;
        ev.inkAfterLineRemoval = ev.hasInk;
        ev.lowConfidence = ev.hasInk && line.confidence >= 0 && line.confidence < acceptThreshold;
    }

    static boolean blank(OcrLine line) {
        if (line == null) {
            return true;
        }
        if (line.empty != null) {
            return line.empty.booleanValue();
        }
        if (line.text != null && !line.text.trim().isEmpty()) {
            return false;
        }
        if (line.confidence >= 0 && line.confidence < 0.85d) {
            return false;
        }
        return true;
    }

    static String text(OcrLine line) {
        return line == null || line.text == null ? "" : line.text.trim();
    }
}
