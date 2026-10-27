package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionPromptJsonContrastTest {

    @Test
    void stripsEightOneExamplesButKeepsEightTwoContrastJson() {
        String prompt = ""
                + "【8.1】输出示例\n"
                + "示例1：印刷日期\n"
                + "<<<1>>>\n"
                + "[\"1\",\"France\",\"MARLY\",\"2026-09-07\",\"Ahmed Yahia Samy\",\"STAFFMATCH\",\"08:00-14:00\",\"08:00\",\"14:00\",\"0\",\"已签字\",\"\",\"正常\",\"false\",\"\",\"09/07/2026\"]\n"
                + "\n"
                + "【8.2】同页混排 JSON 对照（禁止把 NAME_ABSENT / NAME_PRESENT / NAME_REWRITE 当识别结果）\n"
                + "<<<1>>>\n"
                + "[\"1\",\"France\",\"WAREHOUSE\",\"2026-09-21\",\"NAME_ABSENT\",\"AGENCY\",\"16:00-23:00\",\"\",\"\",\"0\",\"\",\"ABSENT\",\"未出勤\",\"false\",\"\",\"09/21/2026\"]\n"
                + "<<<2>>>\n"
                + "[\"2\",\"France\",\"WAREHOUSE\",\"2026-09-21\",\"NAME_PRESENT\",\"AGENCY\",\"16:00-23:00\",\"16:20\",\"22:50\",\"0\",\"已签字\",\"\",\"正常\",\"false\",\"\",\"09/21/2026\"]\n"
                + "<<<3>>>\n"
                + "[\"3\",\"Netherlands\",\"WAREHOUSE\",\"2026-05-10\",\"NAME_REWRITE\",\"AGENCY\",\"12:00-20:00\",\"\",\"\",\"0\",\"\",\"\",\"未出勤\",\"false\",\"\",\"05/10/2026\"]\n";

        String prepared = new RecognitionQualityGuard().preparePromptWithoutExamples(prompt);

        assertFalse(prepared.contains("Ahmed Yahia Samy"), "8.1 JSON should be stripped");
        assertFalse(prepared.contains("08:00-14:00"), "stripped 8.1 must not leave morning-shift copy-bait");
        assertFalse(prepared.contains("\"09:00\""), "surviving text must not plant 09:00");
        assertFalse(prepared.contains("\"14:00\""), "surviving text must not plant 14:00");
        assertTrue(prepared.contains("NAME_ABSENT"), "8.2 contrast must survive stripping");
        assertTrue(prepared.contains("NAME_PRESENT"), "8.2 contrast must survive stripping");
        assertTrue(prepared.contains("NAME_REWRITE"), "rewritten-name contrast must survive stripping");
        assertTrue(prepared.contains("\"16:20\""));
        assertTrue(prepared.contains("\"未出勤\""));
        assertFalse(prepared.contains("看不清用 ???"), "injected stripper text must not plant ???");
    }

    @Test
    void apiConstraintLeadsWithIronLawAndEmptyClockJsonOnly() {
        String constraint = RecognitionPromptGuard.API_OUTPUT_CONSTRAINT;
        assertTrue(constraint.contains("铁律"));
        assertTrue(constraint.contains("宁可空"));
        assertTrue(constraint.contains("NAME_ABSENT"));
        assertTrue(constraint.contains("\"\",\"\""));
        assertFalse(constraint.contains("08:00-14:00"), "API constraint must not plant morning shift");
        assertFalse(constraint.contains("\"09:00\""), "API constraint must not plant 09:00");
        assertFalse(constraint.contains("\"14:00\""), "API constraint must not plant 14:00");
        assertFalse(constraint.contains("\"16:20\""), "API constraint should keep empty-clock JSON only");
        assertFalse(constraint.contains("\"22:50\""), "API constraint should keep empty-clock JSON only");
    }

    @Test
    void filtersSchemaPlaceholderNamesFromResults() {
        RecognitionPromptGuard guard = new RecognitionPromptGuard();
        JSONObject absent = new JSONObject();
        absent.put("NO", "1");
        absent.put("NOM_PRENOM", "NAME_ABSENT");
        JSONObject rewrite = new JSONObject();
        rewrite.put("NO", "3");
        rewrite.put("NOM_PRENOM", "NAME_REWRITE");
        JSONObject real = new JSONObject();
        real.put("NO", "1");
        real.put("NOM_PRENOM", "JOSEPH Lucien");

        assertTrue(guard.isPromptExampleRecord(absent));
        assertTrue(guard.isPromptExampleRecord(rewrite));
        assertFalse(guard.isPromptExampleRecord(real));
    }

    @Test
    void survivingCanonicalFrPromptDoesNotPlantMorningLeakTimes() throws Exception {
        String markdown;
        try (InputStream in = RecognitionPromptJsonContrastTest.class.getResourceAsStream("/canonical/prompts.md")) {
            assertNotNull(in, "canonical/prompts.md missing from test classpath");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            markdown = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
        Map<String, PromptCanonicalParser.ParsedPrompt> parsed = PromptCanonicalParser.parse(markdown);
        String prepared = new RecognitionPromptGuard().preparePromptForApi(
                parsed.get("FR").aiPrompt, new RecognitionQualityGuard());

        assertTrue(prepared.contains("【0】铁律"));
        assertTrue(prepared.contains("NAME_ABSENT"));
        assertTrue(prepared.contains("16:20"));
        assertFalse(prepared.contains("08:00-14:00"), "surviving FR prompt must not plant 08:00-14:00");
        assertFalse(prepared.contains("\"09:00\""), "surviving FR prompt must not plant 09:00");
        assertFalse(prepared.contains("\"14:00\""), "surviving FR prompt must not plant 14:00");
        assertFalse(prepared.contains("看不清用 ???"));
        assertTrue(prepared.endsWith("宁可空，不许抄") || prepared.contains("输出前自检第8、9位"));
    }
}
