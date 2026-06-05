package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureMarkResolverTest {

    @Test
    void blankSignatureIsUnsignedConfirmed() {
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED, SignatureMarkResolver.resolve("", "DUPONT Jean"));
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED, SignatureMarkResolver.resolve("???", "DUPONT Jean"));
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED, SignatureMarkResolver.resolve("员工签名", "DUPONT Jean"));
    }

    @Test
    void matchingSignatureIsSignedConfirmed() {
        assertEquals(SignatureMarkResolver.SIGNED_CONFIRMED, SignatureMarkResolver.resolve("DUPONT", "DUPONT Jean"));
        assertEquals(SignatureMarkResolver.SIGNED_CONFIRMED, SignatureMarkResolver.resolve("Dupont Jean", "DUPONT Jean"));
    }

    @Test
    void lowMatchIsSignedOnly() {
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("XY", "DUPONT Jean"));
        assertEquals(SignatureMarkResolver.SIGNED, SignatureMarkResolver.resolve("Martin", "DUPONT Jean"));
    }

    @Test
    void matchRatioAtLeastSeventyPercentForSameSurname() {
        assertTrue(SignatureMarkResolver.matchRatio("DUPONT", "DUPONT Jean") >= 0.70);
    }

    @Test
    void normalizeLegacySignatureKeepsMarkTokens() {
        assertEquals(SignatureMarkResolver.SIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature("已签字确认"));
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature("未签字确认"));
        assertEquals(SignatureMarkResolver.SIGNED,
                SignatureMarkResolver.normalizeLegacySignature("已签字"));
    }

    @Test
    void normalizeLegacySignatureDefaultsOldTextToSignedConfirmed() {
        assertEquals(SignatureMarkResolver.SIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature("DUPONT Jean"));
        assertEquals(SignatureMarkResolver.SIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature("员工签名A"));
    }

    @Test
    void normalizeLegacySignatureBlankToUnsignedConfirmed() {
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature(""));
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature("???"));
        assertEquals(SignatureMarkResolver.UNSIGNED_CONFIRMED,
                SignatureMarkResolver.normalizeLegacySignature("员工签名"));
    }
}
