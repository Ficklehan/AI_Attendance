package com.attendance.recognition;

import com.attendance.recognition.dto.CellOcrResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrLinePickerTest {

    @Test
    void shiftSkipsCjkGarbageAndKeepsRange() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("華が華");
        ocr.lines.add("10:00-19:00");
        ocr.text = "華が華 10:00-19:00";
        assertEquals("10:00-19:00", OcrLinePicker.shift(ocr));
    }

    @Test
    void nameJoinsLatinAndDropsCjk() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("SISSOKO");
        ocr.lines.add("MODIBO");
        ocr.lines.add("广");
        assertEquals("SISSOKO MODIBO", OcrLinePicker.name(ocr));
    }

    @Test
    void numberPrefersRowIndexWhenBothOneAndThirteen() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("1");
        ocr.lines.add("13");
        assertEquals("13", OcrLinePicker.number(ocr, 13));
        assertEquals("13", OcrLinePicker.number(ocr));
    }

    @Test
    void numberPicksStandaloneDigitsNotBracket() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("]");
        ocr.lines.add("6");
        assertEquals("6", OcrLinePicker.number(ocr));
    }

    @Test
    void clockPadsSingleDigitMinutes() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("19:0");
        assertEquals("19:00", OcrLinePicker.clock(ocr));
    }

    @Test
    void clockRejectsBareDigits() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("0");
        assertEquals("", OcrLinePicker.clock(ocr));
        ocr = new CellOcrResult();
        ocr.lines.add("0010");
        assertEquals("", OcrLinePicker.clock(ocr));
        ocr = new CellOcrResult();
        ocr.lines.add("280:0");
        assertEquals("", OcrLinePicker.clock(ocr));
    }

    @Test
    void nameFoldsDuplicatedLine() {
        CellOcrResult ocr = new CellOcrResult();
        ocr.lines.add("SCARLOSFELIX GONZALEZROSALES");
        ocr.lines.add("SCARLOSFELIX GONZALEZROSALES");
        assertEquals("SCARLOSFELIX GONZALEZROSALES", OcrLinePicker.name(ocr));
    }

    @Test
    void gluedNameNeedsVlm() {
        assertTrue(OcrLinePicker.needsNameVlm("SISSOKOMODIBO"));
        assertTrue(OcrLinePicker.needsNameVlm("MANGUERAGENERUWEERAMOS SISSOKOMODIBC"));
        assertFalse(OcrLinePicker.needsNameVlm("GAYE"));
        assertFalse(OcrLinePicker.needsNameVlm(""));
    }
}
