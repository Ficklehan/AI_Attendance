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
            assertFalse(ai.contains("17/05/2026→2026-05-17"), code + " still has DMY example");
            assertTrue(p.continuePrompt.contains("接续"), code);
        }
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
