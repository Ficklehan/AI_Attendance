package com.attendance.recognition;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.AttendanceApplication;
import com.attendance.config.RecognitionProperties;
import com.attendance.service.RecognitionTrace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 用 Roma 28/10、29/10 两张表跑 dsh headless extract，对照 gold JSON，写完整识别日志。
 * 运行：bash scripts/eval-roma-sheets.sh
 */
@SpringBootTest(classes = AttendanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.task.scheduling.enabled=false",
                "attendance.recognition.debug=true"
        })
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "roma.sheet.eval", matches = "true")
class RomaSheetClockEvalIT {

    private static final Logger log = LoggerFactory.getLogger(RomaSheetClockEvalIT.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Autowired
    private StructuredRecognitionPipeline pipeline;

    @Autowired
    private RecognitionProperties properties;

    @BeforeAll
    static void loadEnv() {
        AttendanceApplication.loadDotenv();
    }

    @Test
    void romaSheetsClocksMatchGoldJson() throws Exception {
        assumeTrue(pipeline.doctorOk(), "dsh/OCR 不可用: " + pipeline.doctorMessage());
        properties.setDebug(true);
        Path outDir = evalDir();
        Files.createDirectories(outDir);
        properties.setDebugDir(outDir.toString());

        StringBuilder report = new StringBuilder();
        List<String> mismatches = new ArrayList<String>();
        report.append("roma sheets eval ").append(STAMP.format(LocalDateTime.now())).append('\n');
        report.append("outDir=").append(outDir.toAbsolutePath()).append('\n');

        evalOne(outDir, "roma-foglio-20261028", "sheets/roma-foglio-20261028.jpg",
                "sheets/roma-foglio-20261028.gold.json", report, mismatches);
        evalOne(outDir, "roma-presenze-20261029", "sheets/roma-presenze-20261029.jpg",
                "sheets/roma-presenze-20261029.gold.json", report, mismatches);

        report.append('\n').append("totalMismatches=").append(mismatches.size()).append('\n');
        for (int i = 0; i < mismatches.size(); i++) {
            report.append("- ").append(mismatches.get(i)).append('\n');
        }
        Files.write(outDir.resolve("report.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(outDir.resolve("mismatches.json"),
                JSON.toJSONString(mismatches, true).getBytes(StandardCharsets.UTF_8));
        log.info("[roma-eval] 日志目录 {}\n{}", outDir.toAbsolutePath(), report);

        assertTrue(mismatches.isEmpty(),
                "Roma 表时钟与 gold 不一致 (" + mismatches.size() + "):\n" + report
                        + "\n详情 " + outDir.toAbsolutePath());
    }

    private void evalOne(Path outDir, String id, String imagePath, String goldPath,
                         StringBuilder report, List<String> mismatches) throws Exception {
        byte[] image = readResource(imagePath);
        JSONObject gold = JSON.parseObject(new String(readResource(goldPath), StandardCharsets.UTF_8));
        RecognitionTrace trace = new RecognitionTrace(id, "eval-it");
        long start = System.currentTimeMillis();
        List<JSONObject> records = pipeline.recognizePage(image, id + ".jpg", "IT", "IT", trace);
        long elapsed = System.currentTimeMillis() - start;

        Path sheetDir = outDir.resolve(id);
        Files.createDirectories(sheetDir);
        Files.write(sheetDir.resolve("records.json"),
                JSON.toJSONString(records, true).getBytes(StandardCharsets.UTF_8));
        Files.write(sheetDir.resolve("trace.json"),
                JSON.toJSONString(trace.toJson(), true).getBytes(StandardCharsets.UTF_8));
        Files.write(sheetDir.resolve("gold.json"),
                JSON.toJSONString(gold, true).getBytes(StandardCharsets.UTF_8));
        JSONObject timing = RecognitionDebugDumper.timingOf(trace);
        timing.put("wallMs", Long.valueOf(elapsed));
        Files.write(sheetDir.resolve("timing.json"),
                JSON.toJSONString(timing, true).getBytes(StandardCharsets.UTF_8));
        Files.write(sheetDir.resolve("flash.json"),
                JSON.toJSONString(RecognitionDebugDumper.flashOf(trace), true).getBytes(StandardCharsets.UTF_8));

        JSONArray expectedRows = gold.getJSONArray("rows");
        List<String> sheetMiss = new ArrayList<String>();
        report.append('\n').append("=== ").append(id).append(" wallMs=").append(elapsed)
                .append(" pageMs=").append(timing.get("pageMs"))
                .append(" visionGround=").append(timing.get("visionGround"))
                .append(" cropZoom=").append(timing.get("cropZoom"))
                .append(" ocrImage=").append(timing.get("ocrImage"))
                .append(" visionDescribe=").append(timing.get("visionDescribe"))
                .append(" toolCalls=").append(timing.get("toolCalls"))
                .append(" workDir=").append(timing.get("workDir"))
                .append(" ===\n");
        int visionClock = timing.getIntValue("visionDescribe");
        int ocrCalls = timing.getIntValue("ocrImage");
        int visionGround = timing.getIntValue("visionGround");
        int cropZoom = timing.getIntValue("cropZoom");
        if (visionGround < 1) {
            String miss = id + " vision_ground=" + visionGround + " expected >=1 (column isolation)";
            sheetMiss.add(miss);
            mismatches.add(miss);
        }
        if (cropZoom < 1) {
            String miss = id + " crop_zoom=" + cropZoom + " expected >=1 (column strips)";
            sheetMiss.add(miss);
            mismatches.add(miss);
        }
        if (visionClock > 6) {
            String miss = id + " vision_describe=" + visionClock + " expected <=6 (column strips, not per cell)";
            sheetMiss.add(miss);
            mismatches.add(miss);
        }
        if (ocrCalls > 0) {
            String miss = id + " ocr_image=" + ocrCalls + " expected 0 (Flash on strips, no local OCR)";
            sheetMiss.add(miss);
            mismatches.add(miss);
        }
        report.append("records=").append(records.size()).append(" gold=").append(expectedRows.size()).append('\n');
        report.append(String.format("%-4s %-8s %-8s %-8s %-8s %-8s %-6s %s%n",
                "NO", "gotA", "expA", "gotD", "expD", "ok", "struck", "name"));

        int n = Math.min(records.size(), expectedRows.size());
        for (int i = 0; i < n; i++) {
            JSONObject got = records.get(i);
            JSONObject exp = expectedRows.getJSONObject(i);
            String no = safe(got.getString("NO"));
            String name = safe(got.getString("NOM_PRENOM"));
            String gotA = clock(got.getString("ARRIVEE"));
            String gotD = clock(got.getString("DEPAR"));
            String expA = clock(exp.getString("arrive"));
            String expD = clock(exp.getString("depart"));
            boolean clocksOk = expA.equals(gotA) && expD.equals(gotD);
            String needle = exp.getString("nameContains");
            boolean nameOk = needle == null || needle.isEmpty()
                    || name.toUpperCase().contains(needle.toUpperCase());
            boolean expStruck = exp.getBooleanValue("nameStruck");
            boolean gotStruck = got.getBooleanValue("_nameStruck") || got.getBooleanValue("isDeleted");
            boolean struckOk = !expStruck || gotStruck;
            String line = String.format("%-4s %-8s %-8s %-8s %-8s %-8s %-6s %s",
                    no, emptyDash(gotA), emptyDash(expA), emptyDash(gotD), emptyDash(expD),
                    clocksOk ? "OK" : "NO",
                    gotStruck ? "Y" : "n",
                    name + " mark=" + safe(got.getString("Mark")));
            report.append(line).append('\n');
            log.info("[roma-eval][{}] {}", id, line);
            if (!clocksOk) {
                String miss = id + " NO " + no + " clocks got " + gotA + "/" + gotD
                        + " expected " + expA + "/" + expD
                        + " mark=" + got.getString("Mark")
                        + " flashA=" + flashHint(got, "ARRIVEE")
                        + " flashD=" + flashHint(got, "DEPAR");
                sheetMiss.add(miss);
                mismatches.add(miss);
            }
            if (!nameOk) {
                String miss = id + " NO " + no + " name missing " + needle + " got=" + name;
                sheetMiss.add(miss);
                mismatches.add(miss);
            }
            if (!struckOk) {
                String miss = id + " NO " + no + " nameStruck expected, got _nameStruck="
                        + got.getBooleanValue("_nameStruck") + " isDeleted=" + got.getBooleanValue("isDeleted");
                sheetMiss.add(miss);
                mismatches.add(miss);
            }
        }
        if (records.size() != expectedRows.size()) {
            String miss = id + " rowCount got=" + records.size() + " expected=" + expectedRows.size();
            sheetMiss.add(miss);
            mismatches.add(miss);
        }
        report.append("sheetMismatches=").append(sheetMiss.size()).append('\n');
        Files.write(sheetDir.resolve("mismatches.json"),
                JSON.toJSONString(sheetMiss, true).getBytes(StandardCharsets.UTF_8));
        Files.write(sheetDir.resolve("report.txt"),
                report.substring(report.lastIndexOf("=== " + id)).getBytes(StandardCharsets.UTF_8));
    }

    private static String flashHint(JSONObject rec, String field) {
        String p = "ARRIVEE".equals(field) ? "arrivo" : "uscita";
        Object time = rec.get("_" + p + "FlashTime");
        Object raw = rec.get("_" + p + "FlashTimeRaw");
        Object shape = rec.get("_" + p + "ShapeCorrected");
        return "time=" + time + " raw=" + raw + " shape=" + shape;
    }

    private static Path evalDir() {
        Path fromBackend = Paths.get(System.getProperty("user.dir", "."), "..", "logs",
                "recognition-debug", "roma-eval-" + STAMP.format(LocalDateTime.now()));
        return fromBackend.toAbsolutePath().normalize();
    }

    private static byte[] readResource(String path) throws Exception {
        InputStream in = RomaSheetClockEvalIT.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("缺少测试资源 " + path);
        }
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        byte[] buf = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String clock(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw;
    }

    private static String emptyDash(String raw) {
        return raw == null || raw.isEmpty() ? "-" : raw;
    }
}
