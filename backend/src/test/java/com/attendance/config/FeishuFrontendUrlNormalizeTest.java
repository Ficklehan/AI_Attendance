package com.attendance.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeishuFrontendUrlNormalizeTest {

    @Test
    void legacyCallbackGetsAttendancePrefix() {
        assertEquals(
                "https://uat-guanpei.eminxing.com/attendance/feishu/callback",
                FeishuProperties.normalizeFrontendCallbackUrl(
                        "https://uat-guanpei.eminxing.com/feishu/callback"));
    }

    @Test
    void modernCallbackUnchanged() {
        String url = "https://uat-guanpei.eminxing.com/attendance/feishu/callback";
        assertEquals(url, FeishuProperties.normalizeFrontendCallbackUrl(url));
    }

    @Test
    void legacyLoginRootGetsAttendancePrefix() {
        assertEquals(
                "https://uat-guanpei.eminxing.com/attendance/",
                FeishuProperties.normalizeFrontendLoginUrl("https://uat-guanpei.eminxing.com"));
    }
}
