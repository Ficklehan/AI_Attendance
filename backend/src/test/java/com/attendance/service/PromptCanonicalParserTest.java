package com.attendance.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptCanonicalParserTest {

    @Test
    void parseCanonicalPrompts_keepsDateLockAndDoesNotTruncate() throws Exception {
        String markdown = readCanonical();
        Map<String, PromptCanonicalParser.ParsedPrompt> parsed = PromptCanonicalParser.parse(markdown);

        assertTrue(parsed.containsKey("default"));
        assertTrue(parsed.containsKey("FR"));
        assertTrue(parsed.containsKey("CN"));
        assertTrue(parsed.containsKey("NL"));
        assertTrue(parsed.containsKey("IT"));
        assertTrue(parsed.containsKey("ES"));
        assertTrue(parsed.containsKey("DE"));
        assertTrue(parsed.containsKey("US"));

        for (String code : new String[]{"default", "FR", "CN", "DE", "US", "NL", "IT", "ES"}) {
            PromptCanonicalParser.ParsedPrompt p = parsed.get(code);
            assertNotNull(p, code);
            assertTrue(p.isValid(), code);
            String ai = p.aiPrompt;
            assertTrue(ai.contains("DeepSeek-V4"), code + " missing title");
            assertTrue(ai.contains("段A"), code + " missing date lock");
            assertTrue(ai.contains("2026-09-03"), code + " truncated before date examples");
            assertTrue(ai.contains("Robbel"), code + " truncated before output example");
            assertTrue(ai.contains("DATE_RAW"), code + " missing DATE_RAW field");
            assertTrue(ai.contains("页头共用"), code + " missing page-header warehouse/date rule");
            assertTrue(ai.contains("MARLY"), code + " missing MARLY example");
            assertTrue(ai.contains("【0】铁律"), code + " missing iron law");
            assertTrue(ai.contains("宁可空，不许抄"), code + " missing no-copy iron law");
            assertTrue(ai.contains("只读本行本格"), code + " missing cell-only rule");
            assertTrue(ai.contains("有姓名必须保留"), code + " missing keep-named-row rule");
            assertTrue(ai.contains("印刷姓名或手写姓名"), code + " missing printed-name keep rule");
            assertTrue(ai.contains("姓名格涂改"), code + " missing name-correction rule");
            assertTrue(ai.contains("NAME_REWRITE"), code + " missing rewritten-name JSON contrast");
            assertTrue(ai.contains("姓名不含问号"), code + " missing no-question-mark name rule");
            assertTrue(ai.contains("看不清标模糊，禁止抄袭"), code + " missing no-copy rule");
            assertTrue(ai.contains("模糊与未出勤不得同时出现"), code + " missing blur/absent mutex");
            assertTrue(ai.contains("确认无手写"), code + " missing empty-cell absent rule");
            assertTrue(ai.contains("禁止 `\"模糊;未出勤\"`") || ai.contains("禁止把模糊和未出勤"),
                    code + " missing forbidden blur+absent combo");
            assertFalse(ai.contains("17/05/2026→2026-05-17"), code + " still has DMY example");
            assertTrue(p.continuePrompt.contains("接续"), code);
            assertTrue(ai.contains("【8.2】同页混排 JSON 对照"), code + " missing surviving JSON contrast");
            assertTrue(ai.contains("NAME_ABSENT"), code + " missing absent JSON contrast");
            assertTrue(ai.contains("NAME_PRESENT"), code + " missing present JSON contrast");
            assertTrue(ai.contains("禁止把邻行或班次的任何时间"), code + " missing no-borrow times rule");
            assertTrue(ai.contains("输出前自检：本格无手写数字则第8、9位必须是"),
                    code + " missing recency self-check after JSON contrast");
            assertTrue(ai.contains("NAME_ABSENT\",\"AGENCY\",\"16:00-23:00\",\"\",\"\""),
                    code + " surviving absent JSON must keep empty clocks without morning-shift copy-bait");
            assertFalse(ai.contains("【3.2】数字 8 的强制判定流程（最高优先级"),
                    code + " digit-8 heading still claims highest priority");
            assertTrue(p.continuePrompt.contains("铁律：只读本行本格"), code + " continue prompt must lead with iron law");
            assertTrue(p.continuePrompt.contains("有姓名但到离为空的未出勤行也必须输出"),
                    code + " continue prompt must keep named empty-clock rows");
        }

        PromptCanonicalParser.ParsedPrompt fr = parsed.get("FR");
        assertTrue(fr.aiPrompt.contains("印刷冒号"));
        assertTrue(fr.aiPrompt.contains("印刷空行禁止输出"));
        assertTrue(fr.aiPrompt.contains("15:41"));
        assertTrue(fr.aiPrompt.contains("13:41"));
        assertTrue(fr.aiPrompt.contains("只读本格手写，禁止用上下文改写"));
        assertTrue(fr.aiPrompt.contains("格内有笔迹但看不清或没把握"));
        assertTrue(fr.aiPrompt.contains("两侧确认无手写"));
        assertTrue(fr.aiPrompt.contains("ABSENT 只读本格"));
        assertTrue(fr.aiPrompt.contains("禁止把邻行或班次的任何时间"));
        assertFalse(fr.aiPrompt.contains("法国表最高优先级"));
        assertFalse(fr.aiPrompt.contains("必须输出 15:41，不得改成 13:41"));
        assertTrue(fr.aiPrompt.contains("欧洲手写数字"));
        assertFalse(fr.aiPrompt.contains("中国手写数字补充规则"));
        assertTrue(parsed.get("CN").aiPrompt.contains("中国手写数字补充规则"));
    }

    @Test
    void parse_innerHeadingsAndJsonFenceDoNotTruncate() {
        String markdown = ""
                + "## 主要识别提示词\n\n"
                + "```markdown\n"
                + "# Title\n"
                + "## 1. 角色\n"
                + "keep-after-heading\n"
                + "```json\n"
                + "[\"1\"]\n"
                + "```\n"
                + "keep-after-json\n"
                + "```\n\n"
                + "## 继续输出提示词\n\n"
                + "```markdown\n"
                + "continue-body\n"
                + "```\n";
        Map<String, PromptCanonicalParser.ParsedPrompt> parsed = PromptCanonicalParser.parse(markdown);
        PromptCanonicalParser.ParsedPrompt def = parsed.get("default");
        assertNotNull(def);
        assertTrue(def.aiPrompt.contains("keep-after-heading"));
        assertTrue(def.aiPrompt.contains("keep-after-json"));
        assertEquals("continue-body", def.continuePrompt);
    }

    private static String readCanonical() throws Exception {
        try (InputStream in = PromptCanonicalParserTest.class.getResourceAsStream("/canonical/prompts.md")) {
            assertNotNull(in, "canonical/prompts.md missing from test classpath");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
