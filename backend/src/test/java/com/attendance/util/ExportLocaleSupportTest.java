package com.attendance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportLocaleSupportTest {

    @Test
    void resolvesKnownLocale() {
        assertEquals("zh-CN", ExportLocaleSupport.resolveLocale("zh-CN"));
        assertEquals("en-US", ExportLocaleSupport.resolveLocale("en-US"));
    }

    @Test
    void fallsBackForUnknownLocale() {
        assertEquals("en-US", ExportLocaleSupport.resolveLocale("xx-YY"));
    }

    @Test
    void headersAreLocalized() {
        String[] zh = ExportLocaleSupport.headers("zh-CN", "taskList.headers");
        String[] en = ExportLocaleSupport.headers("en-US", "taskList.headers");
        assertTrue(zh.length > 0);
        assertTrue(en.length > 0);
        assertEquals("任务ID", zh[0]);
        assertEquals("Task ID", en[0]);
    }

    @Test
    void taskStatusIsLocalized() {
        assertEquals("已确认", ExportLocaleSupport.formatTaskStatus("zh-CN", "confirmed"));
        assertEquals("Confirmed", ExportLocaleSupport.formatTaskStatus("en-US", "confirmed"));
    }
}
