package com.attendance.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionRetrySupportKeyFailoverTest {

    @Test
    void keyFailover_on401_403_429() {
        assertTrue(RecognitionRetrySupport.isKeyFailover(new MimoApiException(401, "unauthorized")));
        assertTrue(RecognitionRetrySupport.isKeyFailover(new MimoApiException(403, "forbidden")));
        assertTrue(RecognitionRetrySupport.isKeyFailover(new MimoApiException(429, "rate limit")));
        assertTrue(RecognitionRetrySupport.isKeyFailover(new MimoApiException(402, "payment")));
    }

    @Test
    void notKeyFailover_on502() {
        assertFalse(RecognitionRetrySupport.isKeyFailover(new MimoApiException(502, "bad gateway")));
        assertTrue(RecognitionRetrySupport.isRetryable(new MimoApiException(502, "bad gateway")));
    }

    @Test
    void keyFailover_onMessageHint() {
        assertTrue(RecognitionRetrySupport.isKeyFailover(
                new IOException("invalid_api_key provided")));
    }
}
