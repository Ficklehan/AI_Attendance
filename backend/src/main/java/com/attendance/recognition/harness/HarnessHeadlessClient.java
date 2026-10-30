package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.DeepSeekProperties;
import com.attendance.config.RecognitionProperties;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Java 只把原图交给 DeepSeek Harness headless，识别全在 profile 内完成。
 */
@Component
public class HarnessHeadlessClient {

    private static final Logger log = LoggerFactory.getLogger(HarnessHeadlessClient.class);

    private final RecognitionProperties properties;
    private final DeepSeekProperties deepSeekProperties;
    private String lastError = "";

    public HarnessHeadlessClient(RecognitionProperties properties, DeepSeekProperties deepSeekProperties) {
        this.properties = properties;
        this.deepSeekProperties = deepSeekProperties;
    }

    public boolean available() {
        return resolveDsh() != null && profileDir() != null;
    }

    public String doctorMessage() {
        if (resolveDsh() == null) {
            return lastError.isEmpty() ? "dsh 未安装" : lastError;
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
        lastError = "未找到 dsh，安装: npm i -g @deepseek-ai/dsh@alpha";
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
        if (trace != null) {
            JSONObject done = new JSONObject();
            done.put("exit", Integer.valueOf(run.exit));
            done.put("sessionId", run.sessionId);
            done.put("toolCalls", Integer.valueOf(run.toolCalls));
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
        return new ExtractResult(run.answer, run.sessionId, run.toolCalls);
    }

    static String buildTask(Path imagePng, String country, Path skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("Use skill attendance-sheet.\n");
        if (skill != null) {
            sb.append("Skill file: ").append(skill.toAbsolutePath()).append('\n');
            Path prompt = skill.getParent().resolve("references").resolve("prompt.md");
            if (Files.isRegularFile(prompt)) {
                sb.append("Transcription contract: ").append(prompt.toAbsolutePath()).append('\n');
            }
        }
        sb.append("Timesheet image (absolute path): ").append(imagePng.toAbsolutePath()).append('\n');
        sb.append("Country code: ").append(country == null || country.trim().isEmpty() ? "FR" : country.trim())
                .append('\n');
        sb.append("OCR each magnified cell first (ocr_image always). Tiler if the scan is larger than 1024px.\n");
        sb.append("Model only on that cell if OCR is empty or confidence < 0.85. Never copy shift or neighbor times.\n");
        sb.append("Final answer: one JSON object {page_num,warehouse,date_raw,rows:[{row_no,name,name_crossed,arrivo,uscita,...}]}.\n");
        sb.append("Each cell is {text,confidence,bbox:[x1,y1,x2,y2],empty?}. Do not output Mark or 已删除.\n");
        return sb.toString();
    }

    private RunResult run(List<String> cmd, String stdin, Path workDir, int timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectRoot().toFile());
        pb.redirectErrorStream(false);
        if (deepSeekProperties != null) {
            String key = firstKey();
            if (key != null && !key.isEmpty()
                    && (pb.environment().get("DEEPSEEK_API_KEY") == null
                    || pb.environment().get("DEEPSEEK_API_KEY").trim().isEmpty())) {
                pb.environment().put("DEEPSEEK_API_KEY", key);
            }
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
        return parseRun(stdout.text(), stderr.text(), process.exitValue());
    }

    static RunResult parseRun(String stdout, String stderr, int exit) {
        RunResult r = new RunResult();
        r.exit = exit;
        r.stderr = stderr == null ? "" : stderr;
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
                    if ("tool_call".equals(type) || "tool_result".equals(type)) {
                        r.toolCalls++;
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
            return r;
        }
        r.answer = text;
        return r;
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

    private String firstKey() {
        if (deepSeekProperties == null) {
            return "";
        }
        List<String> keys = deepSeekProperties.getResolvedApiKeys();
        if (keys != null && !keys.isEmpty() && keys.get(0) != null) {
            return keys.get(0).trim();
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

        public ExtractResult(String answer, String sessionId, int toolCalls) {
            this.answer = answer == null ? "" : answer;
            this.sessionId = sessionId == null ? "" : sessionId;
            this.toolCalls = toolCalls;
        }
    }

    static final class RunResult {
        int exit;
        String answer = "";
        String stderr = "";
        String sessionId = "";
        int toolCalls;
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
