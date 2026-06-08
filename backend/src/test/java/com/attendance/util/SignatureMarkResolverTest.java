package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureMarkResolverTest {

    @Test
    void sanitizeAiSignatureStripsColumnHeaderEcho() {
        assertEquals("", SignatureMarkResolver.sanitizeAiSignature("SIGNATURE"));
        assertEquals("", SignatureMarkResolver.sanitizeAiSignature("Signature"));
        assertEquals("", SignatureMarkResolver.sanitizeAiSignature("Firma"));
        assertEquals("", SignatureMarkResolver.sanitizeAiSignature("员工签名"));
        assertEquals("Dupont", SignatureMarkResolver.sanitizeAiSignature("Dupont"));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.resolve(SignatureMarkResolver.sanitizeAiSignature("SIGNATURE")));
    }

    @Test
    void blankSignatureIsUnsigned() {
        assertEquals(SignatureMarkResolver.UNSIGNED, SignatureMarkResolver.resolve(""));
        assertEquals(SignatureMarkResolver.UNSIGNED, SignatureMarkResolver.resolve("员工签名"));
        assertEquals(SignatureMarkResolver.UNSIGNED, SignatureMarkResolver.resolve("Firma"));
    }

    @Test
    void illegibleSignatureIsSigned() {
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("???"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("??"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("illegible"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("模糊"));
    }

    @Test
    void deletedRowSignatureIsUnsigned() {
        assertEquals(SignatureMarkResolver.UNSIGNED, SignatureMarkResolver.resolve("Dupont", true));
        assertEquals(SignatureMarkResolver.UNSIGNED, SignatureMarkResolver.resolve("???", true));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.normalizeLegacySignature("已签字", true));
    }

    @Test
    void rowDeletedDetectedFromSmartMark() {
        assertTrue(SignatureMarkResolver.isRowDeletedForSignature(false, "正常;已删除"));
        assertFalse(SignatureMarkResolver.isRowDeletedForSignature(false, "模糊;正常"));
    }

    @Test
    void anyRecognizedTextIsSigned() {
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("DUPONT"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("Dupont Jean"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("XY"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("Martin"));
    }

    @Test
    void resolveIgnoresEmployeeName() {
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("Martin", "DUPONT Jean"));
        assertEquals(SignatureMarkResolver.UNSIGNED, SignatureMarkResolver.resolve("", "DUPONT Jean"));
    }

    @Test
    void normalizeLegacySignatureMapsToTwoStates() {
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.normalizeLegacySignature("已签字确认"));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.normalizeLegacySignature("未签字确认"));
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.normalizeLegacySignature("已签字"));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.normalizeLegacySignature("未签字"));
    }

    @Test
    void normalizeLegacySignatureDefaultsOldTextToSigned() {
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.normalizeLegacySignature("DUPONT Jean"));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.normalizeLegacySignature("员工签名A"));
    }

    @Test
    void resolveFromAiOutputInfersSignedWhenAttendancePresent() {
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.resolveFromAiOutput("", false, "正常", "12:00", "", "正常"));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.resolveFromAiOutput("", false, "未出勤", "", "", "未出勤"));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.resolveFromAiOutput("", true, "正常", "12:00", "", "正常"));
    }

    @Test
    void resolveFromAiOutputStruckOutIsUnsigned() {
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.resolveFromAiOutput("划线删除", false, "正常", "12:00", "", "正常"));
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.resolveFromAiOutput("Sangare", false, "正常", "12:00", "", "正常"));
    }

    @Test
    void signatureColumnHeaderTextAllowsExtraWords() {
        assertTrue(SignatureMarkResolver.isSignatureColumnHeaderText("Firma del dipendente"));
        assertFalse(SignatureMarkResolver.isSignatureColumnHeaderText("Firma e conferma responsabile"));
    }

    @Test
    void normalizeLegacySignatureBlankToUnsigned() {
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.normalizeLegacySignature(""));
        assertEquals(SignatureMarkResolver.UNSIGNED,
                SignatureMarkResolver.normalizeLegacySignature("员工签名"));
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.normalizeLegacySignature("???"));
    }
}
