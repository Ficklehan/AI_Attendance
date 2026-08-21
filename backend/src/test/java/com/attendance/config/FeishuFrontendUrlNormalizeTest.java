package com.attendance.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeishuFrontendUrlNormalizeTest {

    @Test
    void legacyBareCallbackGetsClockaiPrefix() {
        assertEquals(
                "https://uat-guanpei.eminxing.com/clockai/feishu/callback",
                FeishuProperties.normalizeFrontendCallbackUrl(
                        "https://uat-guanpei.eminxing.com/feishu/callback"));
    }

    @Test
    void legacyAttendanceCallbackMigratesToClockai() {
        assertEquals(
                "https://uat-guanpei.eminxing.com/clockai/feishu/callback",
                FeishuProperties.normalizeFrontendCallbackUrl(
                        "https://uat-guanpei.eminxing.com/attendance/feishu/callback"));
    }

    @Test
    void modernCallbackUnchanged() {
        String url = "https://uat-guanpei.eminxing.com/clockai/feishu/callback";
        assertEquals(url, FeishuProperties.normalizeFrontendCallbackUrl(url));
    }

    @Test
    void legacyLoginRootGetsClockaiPrefix() {
        assertEquals(
                "https://uat-guanpei.eminxing.com/clockai/",
                FeishuProperties.normalizeFrontendLoginUrl("https://uat-guanpei.eminxing.com"));
    }

    @Test
    void legacyAttendanceLoginMigratesToClockai() {
        assertEquals(
                "https://uat-guanpei.eminxing.com/clockai/",
                FeishuProperties.normalizeFrontendLoginUrl(
                        "https://uat-guanpei.eminxing.com/attendance/"));
    }
}
