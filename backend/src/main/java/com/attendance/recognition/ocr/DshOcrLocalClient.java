package com.attendance.recognition.ocr;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.dto.CellOcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 调用 dsh-ocr-local：常驻 ocr-serve（同一份 ocr.py / ONNX），失败再回退一次性 ocr.py --full。
 */
@Component
public class DshOcrLocalClient implements OcrClient {

    private static final Logger log = LoggerFactory.getLogger(DshOcrLocalClient.class);

    private static final long DOCTOR_TTL_MS = 60000L;

    private final RecognitionProperties properties;
    private volatile JSONObject cachedDoctor;
    private volatile long cachedDoctorAt;
    private final Object workerLock = new Object();
    private volatile List<OcrWorker> workers = new ArrayList<OcrWorker>();
    private final AtomicInteger workerCursor = new AtomicInteger();

    public DshOcrLocalClient(RecognitionProperties properties) {
        this.properties = properties;
    }

    @PreDestroy
    public void shutdown() {
        synchronized (workerLock) {
            for (int i = 0; i < workers.size(); i++) {
                workers.get(i).close();
            }
            workers = new ArrayList<OcrWorker>();
        }
    }

    @Override
    public boolean doctorOk() {
        return doctor().getBooleanValue("ok");
    }

    @Override
    public String doctorMessage() {
        JSONObject report = doctor();
        if (report.getBooleanValue("ok")) {
            return "ok";
        }
        return report.toJSONString();
    }

    public JSONObject doctor() {
        long now = System.currentTimeMillis();
        JSONObject cached = cachedDoctor;
        if (cached != null && now - cachedDoctorAt < DOCTOR_TTL_MS) {
            return cached;
        }
        JSONObject fresh = doctorUncached();
        cachedDoctor = fresh;
        cachedDoctorAt = now;
        return fresh;
    }

    JSONObject doctorUncached() {
        Path script = resolveOcrScript();
        if (script == null || !Files.isRegularFile(script)) {
            JSONObject miss = new JSONObject();
            miss.put("ok", false);
            miss.put("error", "dsh-ocr-local ocr.py not found");
            miss.put("pluginRoot", resolvePluginRoot());
            return miss;
        }
        List<String> cmd = new ArrayList<String>();
        cmd.add(resolvePython());
        cmd.add(script.toString());
        cmd.add("--doctor");
        String modelDir = resolveModelDir();
        if (modelDir != null && !modelDir.isEmpty()) {
            cmd.add("--model-dir");
            cmd.add(modelDir);
        }
        try {
            String out = run(cmd, Math.max(3000, properties.getOcr().getTimeoutMs()));
            JSONObject parsed = JSON.parseObject(out.trim());
            return parsed != null ? parsed : fail("doctor empty");
        } catch (Exception e) {
            log.warn("dsh-ocr-local doctor failed: {}", e.getMessage());
            return fail(e.getMessage());
        }
    }

    @Override
    public CellOcrResult ocrCell(Path cropPng) throws Exception {
        if (cropPng == null || !Files.isRegularFile(cropPng)) {
            return CellOcrResult.empty();
        }
        Path script = resolveOcrScript();
        if (script == null || !Files.isRegularFile(script)) {
            throw new IllegalStateException("dsh-ocr-local 未安装: " + resolvePluginRoot());
        }
        try {
            OcrWorker worker = nextWorker();
            if (worker != null) {
                return worker.ocr(cropPng);
            }
        } catch (Exception e) {
            log.warn("ocr-serve 失败，回退一次性 ocr.py: {}", e.getMessage());
            synchronized (workerLock) {
                for (int i = 0; i < workers.size(); i++) {
                    workers.get(i).close();
                }
                workers = new ArrayList<OcrWorker>();
            }
        }
        return ocrOnce(cropPng, script);
    }

    private CellOcrResult ocrOnce(Path cropPng, Path script) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add(resolvePython());
        cmd.add(script.toString());
        cmd.add(cropPng.toAbsolutePath().toString());
        cmd.add("--full");
        String modelDir = resolveModelDir();
        if (modelDir != null && !modelDir.isEmpty()) {
            cmd.add("--model-dir");
            cmd.add(modelDir);
        }
        String out = run(cmd, Math.max(1000, properties.getOcr().getTimeoutMs()));
        return parseFull(out);
    }

    private OcrWorker nextWorker() throws Exception {
        List<OcrWorker> live = workers;
        if (live == null || live.isEmpty()) {
            ensureWorkers();
            live = workers;
        }
        if (live == null || live.isEmpty()) {
            return null;
        }
        int n = live.size();
        int idx = Math.floorMod(workerCursor.getAndIncrement(), n);
        return live.get(idx);
    }

    private void ensureWorkers() throws Exception {
        synchronized (workerLock) {
            if (workers != null && !workers.isEmpty()) {
                return;
            }
            Path serve = resolveServeScript();
            Path ocrPy = resolveOcrScript();
            if (serve == null || ocrPy == null || !Files.isRegularFile(serve) || !Files.isRegularFile(ocrPy)) {
                log.warn("ocr-serve 脚本不存在，走一次性 ocr.py");
                workers = new ArrayList<OcrWorker>();
                return;
            }
            int n = Math.max(1, Math.min(4, properties.getOcr().getParallelism()));
            List<OcrWorker> started = new ArrayList<OcrWorker>();
            try {
                for (int i = 0; i < n; i++) {
                    started.add(OcrWorker.start(resolvePython(), serve, ocrPy, resolveModelDir(),
                            Math.max(3000, properties.getOcr().getTimeoutMs())));
                }
            } catch (Exception e) {
                for (int i = 0; i < started.size(); i++) {
                    started.get(i).close();
                }
                throw e;
            }
            workers = started;
            log.info("dsh-ocr-local serve 已启动 workers={}", started.size());
        }
    }

    Path resolveServeScript() {
        String userDir = System.getProperty("user.dir");
        Path[] candidates = new Path[] {
                Paths.get(userDir, "scripts", "dsh-ocr-serve.py"),
                Paths.get(userDir, "..", "scripts", "dsh-ocr-serve.py"),
                Paths.get(userDir).getParent() != null
                        ? Paths.get(userDir).getParent().resolve("scripts").resolve("dsh-ocr-serve.py")
                        : null
        };
        for (int i = 0; i < candidates.length; i++) {
            Path p = candidates[i];
            if (p != null && Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public static CellOcrResult parseFull(String json) {
        CellOcrResult result = new CellOcrResult();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        JSONObject root = JSON.parseObject(json.trim());
        if (root == null) {
            return result;
        }
        if (root.containsKey("error")) {
            result.lowConfidence = true;
            return result;
        }
        JSONArray lines = root.getJSONArray("lines");
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        StringBuilder joined = new StringBuilder();
        double minConf = 1.0d;
        boolean anyLow = false;
        for (int i = 0; i < lines.size(); i++) {
            JSONObject line = lines.getJSONObject(i);
            if (line == null) {
                continue;
            }
            String text = line.getString("text");
            if (text != null && !text.trim().isEmpty()) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(text.trim());
                result.lines.add(text.trim());
            }
            double conf = line.getDoubleValue("confidence");
            if (conf > 0 && conf < minConf) {
                minConf = conf;
            }
            if (line.getBooleanValue("low_confidence")) {
                anyLow = true;
            }
        }
        result.text = joined.toString();
        result.confidence = result.lines.isEmpty() ? 0 : minConf;
        result.lowConfidence = anyLow;
        return result;
    }

    Path resolveOcrScript() {
        String root = resolvePluginRoot();
        if (root == null || root.trim().isEmpty()) {
            return null;
        }
        return Paths.get(root, "ocr", "ocr.py");
    }

    String resolvePluginRoot() {
        String configured = properties.getOcr().getPluginRoot();
        if (configured != null && !configured.trim().isEmpty()) {
            return expandHome(configured.trim());
        }
        String profile = properties.getHarness().getProfile();
        if (profile == null || profile.trim().isEmpty()) {
            profile = "attendance";
        }
        String home = System.getProperty("user.home");
        return Paths.get(home, ".dsh", "profiles", profile, "node_modules", "dsh-ocr-local").toString();
    }

    String resolvePython() {
        String configured = properties.getOcr().getPythonPath();
        if (configured != null && !configured.trim().isEmpty()) {
            return expandHome(configured.trim());
        }
        String env = System.getenv("DSH_OCR_PYTHON");
        if (env != null && !env.trim().isEmpty()) {
            return expandHome(env.trim());
        }
        String home = System.getProperty("user.home");
        Path unix = Paths.get(home, ".dsh-ocr", "venv", "bin", "python");
        if (Files.isRegularFile(unix)) {
            return unix.toString();
        }
        Path win = Paths.get(home, ".dsh-ocr", "venv", "Scripts", "python.exe");
        if (Files.isRegularFile(win)) {
            return win.toString();
        }
        return "python3";
    }

    String resolveModelDir() {
        String configured = properties.getOcr().getModelDir();
        if (configured != null && !configured.trim().isEmpty()) {
            return expandHome(configured.trim());
        }
        String env = System.getenv("DSH_OCR_MODELS");
        if (env != null && !env.trim().isEmpty()) {
            return expandHome(env.trim());
        }
        return Paths.get(System.getProperty("user.home"), ".dsh-ocr", "models").toString();
    }

    private static String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private static JSONObject fail(String message) {
        JSONObject o = new JSONObject();
        o.put("ok", false);
        o.put("error", message);
        return o;
    }

    private String run(List<String> cmd, int timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        File models = new File(resolveModelDir());
        if (models.isDirectory()) {
            pb.environment().put("DSH_OCR_MODELS", models.getAbsolutePath());
        }
        Process process = pb.start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        InputStream in = process.getInputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("dsh-ocr-local timeout " + timeoutMs + "ms");
        }
        String out = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0 && (out == null || !out.trim().startsWith("{"))) {
            throw new IllegalStateException("dsh-ocr-local exit " + process.exitValue() + ": " + out);
        }
        return out;
    }

    private static final class OcrWorker {
        private final Process process;
        private final BufferedWriter stdin;
        private final BufferedReader stdout;
        private final int timeoutMs;
        private final AtomicLong seq = new AtomicLong();

        private OcrWorker(Process process, BufferedWriter stdin, BufferedReader stdout, int timeoutMs) {
            this.process = process;
            this.stdin = stdin;
            this.stdout = stdout;
            this.timeoutMs = timeoutMs;
        }

        static OcrWorker start(String python, Path serve, Path ocrPy, String modelDir, int timeoutMs)
                throws Exception {
            List<String> cmd = new ArrayList<String>();
            cmd.add(python);
            cmd.add(serve.toString());
            cmd.add("--ocr-script");
            cmd.add(ocrPy.toAbsolutePath().toString());
            cmd.add("--model-dir");
            cmd.add(modelDir);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            File models = new File(modelDir);
            if (models.isDirectory()) {
                pb.environment().put("DSH_OCR_MODELS", models.getAbsolutePath());
            }
            Process process = pb.start();
            BufferedWriter in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),
                    StandardCharsets.UTF_8));
            BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(),
                    StandardCharsets.UTF_8));
            String ready = null;
            long readyDeadline = System.currentTimeMillis() + 60000L;
            while (System.currentTimeMillis() < readyDeadline) {
                int remain = (int) Math.max(1000L, readyDeadline - System.currentTimeMillis());
                String line = readLineUntil(out, process, remain);
                if (line == null) {
                    break;
                }
                if (line.contains("\"ready\"")) {
                    ready = line;
                    break;
                }
            }
            if (ready == null) {
                process.destroyForcibly();
                throw new IllegalStateException("ocr-serve 未就绪: " + ready);
            }
            return new OcrWorker(process, in, out, timeoutMs);
        }

        synchronized CellOcrResult ocr(Path cropPng) throws Exception {
            if (!process.isAlive()) {
                throw new IllegalStateException("ocr-serve 已退出");
            }
            String id = String.valueOf(seq.incrementAndGet());
            JSONObject req = new JSONObject();
            req.put("id", id);
            req.put("path", cropPng.toAbsolutePath().toString());
            stdin.write(req.toJSONString());
            stdin.newLine();
            stdin.flush();
            String line = readLineUntil(stdout, process, Math.max(1000, timeoutMs));
            if (line == null) {
                throw new IllegalStateException("ocr-serve 无响应");
            }
            return parseFull(line);
        }

        private static String readLineUntil(BufferedReader out, Process process, int timeoutMs)
                throws Exception {
            long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMs);
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive()) {
                    throw new IllegalStateException("ocr-serve 已退出");
                }
                if (out.ready()) {
                    return out.readLine();
                }
                Thread.sleep(10L);
            }
            return null;
        }

        void close() {
            try {
                stdin.write("{\"cmd\":\"quit\"}\n");
                stdin.flush();
            } catch (Exception ignored) {
                // closing
            }
            process.destroy();
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                process.destroyForcibly();
            }
        }
    }
}
