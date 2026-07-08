package com.attendance.util;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeishuUserProfileResolverTest {

    @Test
    void resolveUsesEnNameAndEnterpriseEmail() {
        JSONObject info = new JSONObject();
        info.put("name", "Kento Han");
        info.put("en_name", "Kento Han");
        info.put("enterprise_email", "kento.han@company.com");
        info.put("email", "ou_abc@larksuite.com");

        FeishuUserProfileResolver.Profile profile = FeishuUserProfileResolver.resolve(
                info, "ou_abc123");

        assertEquals("Kento Han", profile.getRealName());
        assertEquals("kento.han@company.com", profile.getEmail());
        assertEquals("kento.han", profile.getUsername());
    }

    @Test
    void resolveRejectsOpenIdPlaceholderEmail() {
        JSONObject info = new JSONObject();
        info.put("name", "魏东兴");
        info.put("email", "ou_8025cc649c1cc0108cc@feishu.cn");

        FeishuUserProfileResolver.Profile profile = FeishuUserProfileResolver.resolve(
                info, "ou_8025cc649c1cc0108cc");

        assertEquals("魏东兴", profile.getRealName());
        assertNull(profile.getEmail());
        assertTrue(profile.getUsername().startsWith("feishu_"));
    }

    @Test
    void isPlaceholderEmailDetectsLarkDomains() {
        assertTrue(FeishuUserProfileResolver.isPlaceholderEmail(
                "ou_abc@larksuite.com", "ou_abc"));
        assertTrue(FeishuUserProfileResolver.isPlaceholderEmail(
                "ou_abc@feishu.internal", "ou_abc"));
    }

    @Test
    void displayEmailHidesPlaceholder() {
        assertNull(FeishuUserProfileResolver.displayEmail("ou_x@feishu.cn", "ou_x"));
        assertEquals("real@corp.com", FeishuUserProfileResolver.displayEmail("real@corp.com", "ou_x"));
    }
}
