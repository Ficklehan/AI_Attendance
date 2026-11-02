package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.DeepSeekProperties;
import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.HarnessColumnCatalog;
import com.attendance.recognition.dto.HarnessSheet;
import com.attendance.service.RecognitionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java 只把原图交给 DeepSeek Harness headless，识别全在 profile 内完成。
 */
@Component
public class HarnessHeadlessClient {

    private static final Logger log = LoggerFactory.getLogger(HarnessHeadlessClient.class);

    private final RecognitionProperties properties;
    private final DeepSeekProperties deepSeekProperties;
    private final AtomicInteger keyCursor = new AtomicInteger();

    public HarnessHeadlessClient(RecognitionProperties properties, DeepSeekProperties deepSeekProperties) {
        this.properties = properties;
        this.deepSeekProperties = deepSeekProperties;
    }

    public boolean available() {
        return resolveDsh() != null && profileDir() != null;
    }

    public String doctorMessage() {
        if (resolveDsh() == null) {
            return "dsh 未安装";
        }
        if (profileDir() == null) {
            return "dsh profile " + profileName() + " 不存在";
        }
        return "ok";
    }

    public Path resolveDsh() {
        Path fromConfig = existingFile(properties.getHarness().getDshBin(), System.getenv("DSH_BIN"));
        if (fromConfig != null) {
            return fromConfig;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(":");
            for (int i = 0; i < dirs.length; i++) {
                Path p = Paths.get(dirs[i], "dsh");
                if (Files.isExecutable(p) && !Files.isDirectory(p)) {
                    return p;
                }
            }
        }
        String home = System.getProperty("user.home");
        Path src = Paths.get(home, ".cache", "oneai-dsh", "deepseek-harness", "node_modules", ".bin", "dsh");
        if (Files.isExecutable(src)) {
            return src;
        }
        Path npxCache = firstNpxDsh(home);
        if (npxCache != null) {
            return npxCache;
        }
        return null;
    }

    public ExtractResult extract(Path imagePng, String country, Path workDir, RecognitionTrace trace)
            throws Exception {
        Path dsh = resolveDsh();
        if (dsh == null) {
            throw new IllegalStateException(doctorMessage());
        }
        Path skill = skillFile();
        List<String> cmd = new ArrayList<String>();
        cmd.add(dsh.toAbsolutePath().toString());
        cmd.add("--profile");
        cmd.add(profileName());
        // --patch is a launcher flag; must sit before headless app args or dsh
        // returns {"type":"error","message":"unknown option '--patch'"} (exit 1).
        Path extractPatch = writeExtractPatch(workDir);
        if (extractPatch != null) {
            cmd.add("--patch");
            cmd.add(extractPatch.toAbsolutePath().toString());
        }
        cmd.add("--approval-policy");
        cmd.add("never");
        cmd.add("--permission-mode");
        cmd.add("workspace-write");
        cmd.add("--add-dir");
        cmd.add(workDir.toAbsolutePath().toString());
        if (skill != null) {
            cmd.add("--add-dir");
            cmd.add(skill.getParent().getParent().toAbsolutePath().toString());
        }
        cmd.add("--json");
        cmd.add("-");
        String task = buildTask(imagePng, country, skill);
        try {
            Files.write(workDir.resolve("dsh-task.txt"), task.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("write dsh-task: {}", e.getMessage());
        }
        if (trace != null) {
            JSONObject start = new JSONObject();
            start.put("bin", dsh.toString());
            start.put("profile", profileName());
            start.put("image", imagePng.toString());
            start.put("country", country);
            start.put("skill", skill != null ? skill.toString() : "");
            start.put("cmd", cmd.toString());
            trace.step("harness_extract_start", start);
        }
        log.info("dsh headless profile={} image={}", profileName(), imagePng);
        RunResult run = run(cmd, task, workDir, properties.getHarness().getExtractTimeoutMs());
        writeRunFiles(workDir, run);
        if (trace != null) {
            JSONObject done = new JSONObject();
            done.put("exit", Integer.valueOf(run.exit));
            done.put("sessionId", run.sessionId);
            done.put("toolCalls", Integer.valueOf(run.toolCalls));
            done.put("tools", run.toolNames);
            done.put("visionGround", Integer.valueOf(countTool(run.toolNames, "vision_ground")));
            done.put("cropZoom", Integer.valueOf(countTool(run.toolNames, "crop_zoom")));
            done.put("ocrImage", Integer.valueOf(countTool(run.toolNames, "ocr_image")));
            done.put("visionDescribe", Integer.valueOf(countTool(run.toolNames, "vision_describe")));
            done.put("visionFailed", Boolean.valueOf(run.visionUnavailable));
            done.put("visionOkFalse", Integer.valueOf(run.toolResultOkFalseCount));
            done.put("answerChars", Integer.valueOf(run.answer == null ? 0 : run.answer.length()));
            done.put("stderrPreview", preview(run.stderr, 800));
            if (run.exit != 0) {
                done.put("error", preview(run.stderr, 400));
            }
            trace.step("harness_extract_done", done);
        }
        if (run.exit != 0) {
            throw new IllegalStateException("dsh headless exit " + run.exit + ": " + preview(run.stderr, 800));
        }
        if (run.answer == null || run.answer.trim().isEmpty()) {
            throw new IllegalStateException("dsh headless 未返回 JSON: " + preview(run.stderr, 400));
        }
        return new ExtractResult(run.answer, run.sessionId, run.toolCalls, run.toolNames,
                run.visionUnavailable, run.toolResultOkFalseCount);
    }

    static String buildTask(Path imagePng, String country, Path skill) {
        String countryCode = country == null || country.trim().isEmpty() ? "FR" : country.trim();
        StringBuilder sb = new StringBuilder();
        // Isolation: Flash must never see SHIFT pixels in the same image as clocks.
        // Do not preload skill/prompt.md. Do not fall back to local OCR (print-only).
        sb.append("European warehouse timesheet OCR. Country=").append(countryCode).append('\n');
        sb.append("Image absolute path: ").append(imagePng.toAbsolutePath()).append('\n');
        sb.append(HarnessColumnCatalog.promptBlock(countryCode));
        sb.append("PIXEL ISOLATION: never vision_describe the original page. ")
                .append("A full-page describe puts SHIFT and clocks in one image and the model fills empty clocks from shift.\n");
        sb.append("First output MUST be vision_ground tool calls. No commentary text before them.\n");
        sb.append("Ground the original image (annotate false) in one batch, boxes only, no times:\n");
        sb.append("- HEADER: band above the body table (warehouse + date). Not the body SHIFT column.\n");
        sb.append("- NAME: full name CELL from its left grid line to its right grid line (whole cell, not just the header text). Must not include SHIFT or clock columns.\n");
        sb.append("- ARRIVEE: full arrival CELL from its left grid line to its right grid line (whole cell incl. handwritten times). Must not include SHIFT/Horaires.\n");
        sb.append("- DEPAR: full departure CELL from its left grid line to its right grid line (whole cell incl. handwritten times). Must not include SHIFT or ARRIVEE.\n");
        sb.append("- SHIFT: full printed shift CELL from its left grid line to its right grid line. Used later for shift.text only.\n");
        sb.append("Then exactly ONE crop_zoom on the original path: sheet false, mode nearest, target 6000, ")
                .append("regions = those 5 full-height column boxes labelled header/name/arrivo/uscita/shift. ")
                .append("Never sheet true (stacking shift with clocks leaks). ")
                .append("Clock crops must not include SHIFT pixels.\n");
        sb.append("NAME/HEADER/SHIFT crops: use vision_describe json false on each crop PNG path only. ")
                .append("Never put the original path in paths. Never put SHIFT crop and a clock crop in the same describe.\n");
        sb.append("NAME crop: full wrapped names and whether the name is struck with no replacement.\n");
        sb.append("HEADER crop: warehouse and date_raw only.\n");
        sb.append("SHIFT crop may be described after clock crops; it may fill shift.text only, never arrivo/uscita.\n");
        sb.append("ARRIVEE crop: use ONE ocr_image (full true) on that whole column strip, call ocr_setup first if needed. ")
                .append("OCR reads the column top-to-bottom, one line per body row in order. Each line is handwritten HH:MM, or empty if that row has no ink / only a printed colon. Do not invent times.\n");
        sb.append("DEPAR crop: use ONE ocr_image (full true) on that whole column strip, same top-to-bottom row mapping.\n");
        sb.append("Do not call bash or read_image. Do not vision_describe the clock crops (use ocr_image for them).\n");
        sb.append("Then emit one JSON object {page_num,warehouse,date_raw,rows:[{row_no,name,")
                .append("name_crossed,arrivo,uscita,agency,shift,pausa,signature,observations}]}. ")
                .append("page_num/warehouse/date_raw strings. name_crossed boolean. Each cell ")
                .append("{text,confidence,bbox:[x1,y1,x2,y2],empty?} with empty boolean.\n");
        sb.append("Merge by row order: arrivo ONLY from the ARRIVEE crop describe; uscita ONLY from the DEPAR crop describe. ")
                .append("If that crop says empty, that clock stays empty even when shift is 10:00-19:00. ")
                .append("Never copy SHIFT describe text into arrivo/uscita.\n");
        sb.append("Final answer: that JSON object only. No Mark / 已删除 / markdown fence. Never emit rows:[].\n");
        return sb.toString();
    }

    private RunResult run(List<String> cmd, String stdin, Path workDir, int timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectRoot().toFile());
        pb.redirectErrorStream(false);
        String key = nextKey();
        String key2 = nextKey();
        if (key != null && !key.isEmpty()) {
            pb.environment().put("DEEPSEEK_API_KEY", key);
        }
        if (key2 != null && !key2.isEmpty()) {
            pb.environment().put("DEEPSEEK_API_KEY_2", key2);
        }
        Process process = pb.start();
        OutputStream out = process.getOutputStream();
        try {
            out.write(stdin.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
        StreamCollector stdout = new StreamCollector(process.getInputStream());
        StreamCollector stderr = new StreamCollector(process.getErrorStream());
        stdout.start();
        stderr.start();
        int timeout = timeoutMs > 0 ? timeoutMs : 600000;
        boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            stdout.joinQuiet();
            stderr.joinQuiet();
            throw new IllegalStateException("dsh headless timeout " + timeout + "ms");
        }
        stdout.joinQuiet();
        stderr.joinQuiet();
        RunResult parsed = parseRun(stdout.text(), stderr.text(), process.exitValue());
        parsed.stdout = stdout.text();
        return parsed;
    }

    static RunResult parseRun(String stdout, String stderr, int exit) {
        RunResult r = new RunResult();
        r.exit = exit;
        r.stderr = stderr == null ? "" : stderr;
        r.stdout = stdout == null ? "" : stdout;
        String text = stdout == null ? "" : stdout.trim();
        if (text.isEmpty()) {
            r.answer = "";
            return r;
        }
        if (text.startsWith("{") || text.contains("\n{")) {
            StringBuilder answer = new StringBuilder();
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || !line.startsWith("{")) {
                    continue;
                }
                try {
                    JSONObject ev = JSON.parseObject(line);
                    if (ev == null) {
                        continue;
                    }
                    String type = ev.getString("type");
                    if ("session".equals(type) && r.sessionId.isEmpty()) {
                        r.sessionId = firstString(ev, "sessionId", "id");
                    }
                    recordTools(ev, r);
                    if ("tool_result".equals(type)) {
                        collectToolResult(ev, r);
                    }
                    if ("text".equals(type)) {
                        String chunk = firstString(ev, "text", "delta", "content");
                        if (!chunk.isEmpty()) {
                            if (answer.length() > 0) {
                                answer.append('\n');
                            }
                            answer.append(chunk);
                        }
                    }
                    if ("final".equals(type)) {
                        String fin = firstString(ev, "text", "answer", "content", "message");
                        if (!fin.isEmpty()) {
                            r.answer = fin;
                        }
                    }
                } catch (Exception ignored) {
                    // 非事件行
                }
            }
            if (r.answer == null || r.answer.trim().isEmpty()) {
                r.answer = answer.toString();
            }
            if (r.answer == null || r.answer.trim().isEmpty()) {
                r.answer = text;
            }
            salvageEmptyAnswerFromToolResults(r);
            return r;
        }
        r.answer = text;
        return r;
    }

    static void recordTools(JSONObject ev, RunResult r) {
        if (ev == null || r == null) {
            return;
        }
        String type = ev.getString("type");
        if ("tool_call".equals(type) || "tool_use".equals(type)) {
            r.toolCalls++;
            String tool = toolName(ev);
            if (!tool.isEmpty()) {
                r.toolNames.add(tool);
            }
        } else if ("tool_result".equals(type)) {
            r.toolCalls++;
        }
        if ("tool_call".equals(type) || "tool_use".equals(type) || "tool_result".equals(type)) {
            return;
        }
        JSONObject message = ev.getJSONObject("message");
        if (message != null) {
            recordToolArray(message.getJSONArray("content"), r);
        }
        recordToolArray(ev.getJSONArray("content"), r);
        JSONObject item = ev.getJSONObject("item");
        if (item != null) {
            recordTools(item, r);
        }
    }

    static void recordToolArray(JSONArray arr, RunResult r) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof JSONObject) {
                recordTools((JSONObject) item, r);
            }
        }
    }

    static String toolName(JSONObject ev) {
        if (ev == null) {
            return "";
        }
        String direct = firstString(ev, "name", "toolName", "tool");
        if (!direct.isEmpty() && direct.indexOf('{') < 0) {
            return direct;
        }
        String[] nested = new String[] {"tool", "payload", "data", "arguments"};
        for (int i = 0; i < nested.length; i++) {
            JSONObject inner = ev.getJSONObject(nested[i]);
            if (inner == null) {
                continue;
            }
            String name = firstString(inner, "name", "toolName", "tool");
            if (!name.isEmpty() && name.indexOf('{') < 0) {
                return name;
            }
        }
        return "";
    }

    static void collectToolResult(JSONObject ev, RunResult r) {
        if (ev == null || r == null) {
            return;
        }
        String raw = firstString(ev, "result", "output", "content", "text");
        if (raw.isEmpty()) {
            Object result = ev.get("result");
            if (result != null) {
                raw = String.valueOf(result);
            }
        }
        if (raw.isEmpty()) {
            return;
        }
        if (r.toolResultPreview.isEmpty()) {
            r.toolResultPreview = preview(raw, 400);
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("\"ok\":false") || lower.contains("vision_")) {
            r.toolResultOkFalse = true;
            r.toolResultOkFalseCount++;
        }
        if (lower.contains("vision_backend_unavailable")) {
            r.visionUnavailable = true;
        }
        if (lower.contains("\"text\"")) {
            r.toolResultHasText = true;
        }
        HarnessSheet sheet = HarnessJsonParser.parseSheet(raw);
        if (sheet != null && sheet.rows != null && !sheet.rows.isEmpty()) {
            r.toolResultHasRows = true;
            if (r.salvageAnswer.isEmpty()) {
                r.salvageAnswer = raw;
            }
        }
        r.toolResults.add(raw);
    }

    static void salvageEmptyAnswerFromToolResults(RunResult r) {
        if (r == null) {
            return;
        }
        HarnessSheet answered = HarnessJsonParser.parseSheet(r.answer);
        if (answered != null && answered.rows != null && !answered.rows.isEmpty()) {
            return;
        }
        if (!r.salvageAnswer.isEmpty()) {
            r.answer = r.salvageAnswer;
            r.salvagedFromToolResult = true;
            return;
        }
        for (int i = 0; i < r.toolResults.size(); i++) {
            HarnessSheet sheet = HarnessJsonParser.parseSheet(r.toolResults.get(i));
            if (sheet != null && sheet.rows != null && !sheet.rows.isEmpty()) {
                r.answer = r.toolResults.get(i);
                r.salvagedFromToolResult = true;
                r.toolResultHasRows = true;
                return;
            }
        }
    }

    static int countTool(List<String> names, String want) {
        if (names == null || want == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (want.equals(name)) {
                n++;
            }
        }
        return n;
    }

    private static void writeRunFiles(Path workDir, RunResult run) {
        if (workDir == null || run == null) {
            return;
        }
        try {
            if (run.stdout != null && !run.stdout.isEmpty()) {
                Files.write(workDir.resolve("dsh-stdout.jsonl"),
                        run.stdout.getBytes(StandardCharsets.UTF_8));
            }
            if (run.stderr != null && !run.stderr.isEmpty()) {
                Files.write(workDir.resolve("dsh-stderr.txt"),
                        run.stderr.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.debug("write dsh run files: {}", e.getMessage());
        }
    }

    private static String firstString(JSONObject ev, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            Object v = ev.get(keys[i]);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equals(s)) {
                    return s;
                }
            }
        }
        return "";
    }

    Path skillFile() {
        Path root = projectRoot();
        Path skill = root.resolve(".agents").resolve("skills").resolve("attendance-sheet").resolve("SKILL.md");
        return Files.isRegularFile(skill) ? skill : null;
    }

    Path writeExtractPatch(Path workDir) {
        Path dest = workDir.resolve("attendance-extract.patch.yml");
        try {
            InputStream in = getClass().getResourceAsStream("/dsh/attendance-extract.patch.yml");
            if (in != null) {
                try {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    return dest;
                } finally {
                    in.close();
                }
            }
            Path repo = projectRoot().resolve("backend").resolve("src").resolve("main")
                    .resolve("resources").resolve("dsh").resolve("attendance-extract.patch.yml");
            if (Files.isRegularFile(repo)) {
                Files.copy(repo, dest, StandardCopyOption.REPLACE_EXISTING);
                return dest;
            }
        } catch (Exception e) {
            log.debug("write extract patch: {}", e.getMessage());
        }
        return null;
    }

    Path projectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve(".agents").resolve("skills").resolve("attendance-sheet").resolve("SKILL.md"))
                    || Files.isDirectory(dir.resolve(".git"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
    }

    private String nextKey() {
        if (deepSeekProperties == null) {
            return "";
        }
        List<String> keys = deepSeekProperties.getResolvedApiKeys();
        if (keys != null && !keys.isEmpty()) {
            int n = keys.size();
            int i = (keyCursor.getAndIncrement() & Integer.MAX_VALUE) % n;
            String k = keys.get(i);
            return k == null ? "" : k.trim();
        }
        String k = deepSeekProperties.getApiKey();
        return k == null ? "" : k.trim();
    }

    private Path profileDir() {
        String home = System.getProperty("user.home");
        Path dir = Paths.get(home, ".dsh", "profiles", profileName());
        return Files.isDirectory(dir) ? dir : null;
    }

    private String profileName() {
        String p = properties.getHarness().getProfile();
        return p == null || p.trim().isEmpty() ? "attendance" : p.trim();
    }

    private static Path existingFile(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] == null || candidates[i].trim().isEmpty()) {
                continue;
            }
            Path p = Paths.get(candidates[i].trim());
            if (Files.isRegularFile(p) || Files.isExecutable(p)) {
                return p;
            }
        }
        return null;
    }

    private static Path firstNpxDsh(String home) {
        Path npx = Paths.get(home, ".npm", "_npx");
        if (!Files.isDirectory(npx)) {
            return null;
        }
        try {
            Path[] found = new Path[1];
            Files.walk(npx, 6).forEach(p -> {
                if (found[0] == null && p.endsWith(Paths.get("node_modules", ".bin", "dsh"))
                        && Files.isExecutable(p)) {
                    found[0] = p;
                }
            });
            return found[0];
        } catch (Exception e) {
            return null;
        }
    }

    static String preview(String text, int max) {
        if (text == null) {
            return "";
        }
        String one = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (one.length() <= max) {
            return one;
        }
        return one.substring(0, max) + "...";
    }

    public static final class ExtractResult {
        public final String answer;
        public final String sessionId;
        public final int toolCalls;
        public final List<String> toolNames;
        public final boolean visionUnavailable;
        public final int visionOkFalseCount;

        public ExtractResult(String answer, String sessionId, int toolCalls) {
            this(answer, sessionId, toolCalls, null, false, 0);
        }

        public ExtractResult(String answer, String sessionId, int toolCalls, List<String> toolNames) {
            this(answer, sessionId, toolCalls, toolNames, false, 0);
        }

        public ExtractResult(String answer, String sessionId, int toolCalls, List<String> toolNames,
                             boolean visionUnavailable, int visionOkFalseCount) {
            this.answer = answer == null ? "" : answer;
            this.sessionId = sessionId == null ? "" : sessionId;
            this.toolCalls = toolCalls;
            this.toolNames = toolNames == null ? new ArrayList<String>() : new ArrayList<String>(toolNames);
            this.visionUnavailable = visionUnavailable;
            this.visionOkFalseCount = visionOkFalseCount;
        }

        public int countTool(String name) {
            return HarnessHeadlessClient.countTool(toolNames, name);
        }
    }

    static final class RunResult {
        int exit;
        String answer = "";
        String stderr = "";
        String stdout = "";
        String sessionId = "";
        int toolCalls;
        final List<String> toolNames = new ArrayList<String>();
        final List<String> toolResults = new ArrayList<String>();
        String toolResultPreview = "";
        String salvageAnswer = "";
        boolean toolResultHasRows;
        boolean toolResultHasText;
        boolean toolResultOkFalse;
        int toolResultOkFalseCount;
        boolean visionUnavailable;
        boolean salvagedFromToolResult;
    }

    private static final class StreamCollector extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        StreamCollector(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    buf.write(line.getBytes(StandardCharsets.UTF_8));
                    buf.write('\n');
                }
            } catch (Exception ignored) {
                // process closed
            }
        }

        String text() {
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }

        void joinQuiet() {
            try {
                join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
