package com.attendance.recognition;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.service.RecognitionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 识别调试：切图目录不删，trace / 每格到离全文落到 logs/recognition-debug。
 */
public final class RecognitionDebugDumper {

    private static final Logger log = LoggerFactory.getLogger(RecognitionDebugDumper.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private RecognitionDebugDumper() {
    }

    public static Path createWorkDir(RecognitionProperties properties, RecognitionTrace trace, String filename)
            throws Exception {
        if (properties == null || !properties.isDebug()) {
            return Files.createTempDirectory("clockai-harness-");
        }
        Path root = resolveRoot(properties);
        Files.createDirectories(root);
        String task = trace != null ? traceTask(trace) : "unknown";
        String stem = filename == null || filename.trim().isEmpty() ? "page" : filename.trim();
        stem = stem.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = root.resolve(task + "-" + STAMP.format(LocalDateTime.now()) + "-" + stem);
        Files.createDirectories(dir);
        log.info("识别调试目录: {}", dir.toAbsolutePath());
        return dir;
    }

    public static void persist(Path workDir, RecognitionTrace trace, List<JSONObject> records) {
        if (workDir == null) {
            return;
        }
        try {
            if (trace != null) {
                write(workDir.resolve("trace.json"), JSON.toJSONString(trace.toJson(), true));
            }
            JSONArray clocks = new JSONArray();
            if (records != null) {
                for (int i = 0; i < records.size(); i++) {
                    clocks.add(records.get(i));
                }
            }
            write(workDir.resolve("clocks.json"), JSON.toJSONString(clocks, true));
            write(workDir.resolve("timing.json"), JSON.toJSONString(timingOf(trace), true));
            write(workDir.resolve("flash.json"), JSON.toJSONString(flashOf(trace), true));
            write(workDir.resolve("README.txt"), readme(workDir, trace, records));
            log.info("识别调试已写出 {} (trace+clocks+crops)", workDir.toAbsolutePath());
        } catch (Exception e) {
            log.warn("识别调试写出失败: {}", e.getMessage());
        }
    }

    public static void cleanup(Path workDir, RecognitionProperties properties) {
        if (properties != null && properties.isDebug()) {
            return;
        }
        if (workDir == null || !Files.exists(workDir)) {
            return;
        }
        try {
            java.io.File[] files = workDir.toFile().listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    files[i].delete();
                }
            }
            workDir.toFile().delete();
        } catch (Exception ignored) {
            // temp cleanup
        }
    }

    static Path resolveRoot(RecognitionProperties properties) {
        String configured = properties.getDebugDir();
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        Path fromBackend = Paths.get(System.getProperty("user.dir", "."), "..", "logs", "recognition-debug");
        return fromBackend.toAbsolutePath().normalize();
    }

    private static String traceTask(RecognitionTrace trace) {
        JSONObject json = trace.toJson();
        String id = json.getString("taskId");
        return id == null || id.trim().isEmpty() ? "unknown" : id.trim();
    }

    private static void write(Path file, String text) throws Exception {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String readme(Path workDir, RecognitionTrace trace, List<JSONObject> records) {
        StringBuilder sb = new StringBuilder();
        JSONObject json = trace != null ? trace.toJson() : new JSONObject();
        sb.append("task=").append(json.getString("taskId")).append('\n');
        sb.append("client=").append(json.getString("client")).append('\n');
        sb.append("dir=").append(workDir.toAbsolutePath()).append('\n');
        sb.append("records=").append(records == null ? 0 : records.size()).append('\n');
        sb.append("files: page.jpg|png, harness-answer.json, dsh-stdout.jsonl, trace.json, clocks.json, timing.json\n");
        JSONObject timing = timingOf(trace);
        if (timing.containsKey("pageMs")) {
            sb.append("pageMs=").append(timing.get("pageMs")).append('\n');
        }
        if (timing.containsKey("visionDescribe")) {
            sb.append("visionDescribe=").append(timing.get("visionDescribe")).append('\n');
        }
        if (timing.containsKey("ocrImage")) {
            sb.append("ocrImage=").append(timing.get("ocrImage")).append('\n');
        }
        return sb.toString();
    }

    static JSONObject timingOf(RecognitionTrace trace) {
        JSONObject out = new JSONObject();
        if (trace == null) {
            return out;
        }
        JSONArray steps = trace.toJson().getJSONArray("steps");
        if (steps == null) {
            return out;
        }
        JSONArray phases = new JSONArray();
        int visionDescribe = 0;
        int visionGround = 0;
        int cropZoom = 0;
        int ocrImage = 0;
        int toolCalls = 0;
        for (int i = 0; i < steps.size(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String phase = step.getString("phase");
            JSONObject detail = step.getJSONObject("detail");
            if (detail == null) {
                detail = new JSONObject();
            }
            if ("harness_page_done".equals(phase) || "harness_extract_done".equals(phase)
                    || "harness_json_parsed".equals(phase) || "harness_page_start".equals(phase)
                    || "batch_recognition_parallel".equals(phase)) {
                JSONObject p = new JSONObject();
                p.put("phase", phase);
                if (detail.containsKey("elapsedMs")) {
                    p.put("elapsedMs", detail.get("elapsedMs"));
                }
                if (detail.containsKey("elapsedMs") && "harness_page_done".equals(phase)) {
                    out.put("pageMs", detail.get("elapsedMs"));
                }
                if (detail.containsKey("workDir")) {
                    out.put("workDir", detail.get("workDir"));
                }
                if (detail.containsKey("dshConcurrency")) {
                    out.put("dshConcurrency", detail.get("dshConcurrency"));
                }
                copyCount(detail, out, "visionDescribe");
                copyCount(detail, out, "visionGround");
                copyCount(detail, out, "cropZoom");
                copyCount(detail, out, "ocrImage");
                copyCount(detail, out, "toolCalls");
                copyCount(detail, out, "visionClock");
                phases.add(p);
            }
            if ("harness_extract_done".equals(phase) || "harness_page_done".equals(phase)
                    || "harness_json_parsed".equals(phase)) {
                visionDescribe = Math.max(visionDescribe, detail.getIntValue("visionDescribe"));
                visionGround = Math.max(visionGround, detail.getIntValue("visionGround"));
                cropZoom = Math.max(cropZoom, detail.getIntValue("cropZoom"));
                ocrImage = Math.max(ocrImage, detail.getIntValue("ocrImage"));
                toolCalls = Math.max(toolCalls, detail.getIntValue("toolCalls"));
            }
        }
        out.put("phases", phases);
        out.put("visionDescribe", Integer.valueOf(visionDescribe));
        out.put("visionGround", Integer.valueOf(visionGround));
        out.put("cropZoom", Integer.valueOf(cropZoom));
        out.put("ocrImage", Integer.valueOf(ocrImage));
        out.put("toolCalls", Integer.valueOf(toolCalls));
        out.put("visionClock", Integer.valueOf(visionDescribe));
        return out;
    }

    private static void copyCount(JSONObject from, JSONObject to, String key) {
        if (from != null && from.containsKey(key) && !to.containsKey(key)) {
            to.put(key, from.get(key));
        }
    }

    static JSONArray flashOf(RecognitionTrace trace) {
        JSONArray out = new JSONArray();
        if (trace == null) {
            return out;
        }
        JSONArray steps = trace.toJson().getJSONArray("steps");
        if (steps == null) {
            return out;
        }
        for (int i = 0; i < steps.size(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String phase = step.getString("phase");
            if (!"clock_extract".equals(phase)
                    && !"harness_extract_done".equals(phase)
                    && !"harness_json_parsed".equals(phase)) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("phase", step.getString("phase"));
            row.put("at", step.getString("at"));
            row.put("detail", step.getJSONObject("detail"));
            out.add(row);
        }
        return out;
    }
}
