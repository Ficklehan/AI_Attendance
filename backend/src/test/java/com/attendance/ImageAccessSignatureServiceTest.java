package com.attendance;

import com.attendance.config.JwtProperties;
import com.attendance.security.ImageAccessSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageAccessSignatureServiceTest {

    private ImageAccessSignatureService service;

    @BeforeEach
    void setUp() {
        service = new ImageAccessSignatureService();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-with-enough-length");
        ReflectionTestUtils.setField(service, "jwtProperties", jwtProperties);
    }

    @Test
    void acceptsValidSignature() {
        Map<String, Object> signed = service.sign("20260603_0001.jpg", "user-1");
        long exp = ((Number) signed.get("exp")).longValue();
        assertDoesNotThrow(() -> service.validate(
                "20260603_0001.jpg",
                "user-1",
                exp,
                signed.get("sig").toString()));
    }

    @Test
    void rejectsTamperedSignature() {
        Map<String, Object> signed = service.sign("20260603_0001.jpg", "user-1");
        long exp = ((Number) signed.get("exp")).longValue();
        assertThrows(Exception.class, () -> service.validate(
                "20260603_0001.jpg",
                "user-2",
                exp,
                signed.get("sig").toString()));
    }
}
