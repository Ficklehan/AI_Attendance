package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.dto.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用已安装的 dsh-crop-zoom CLI（crop_zoom 契约）。单次最多 8 格。
 */
@Component
public class CropZoomCli {

    private static final Logger log = LoggerFactory.getLogger(CropZoomCli.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*\\}");
    static final int MAX_REGIONS = 8;
    static final int MIN_SIDE = 4;
    private static final int TIMEOUT_MS = 30000;

    private final RecognitionProperties properties;

    public CropZoomCli(RecognitionProperties properties) {
        this.properties = properties;
    }

    public boolean available() {
        return resolveBin() != null;
    }

    public Path resolveBin() {
        String configured = properties.getHarness().getCropZoomBin();
        Path fromConfig = firstExistingFile(configured, System.getenv("DSH_CROP_ZOOM_BIN"));
        if (fromConfig != null) {
            return fromConfig;
        }
        String profile = properties.getHarness().getProfile();
        if (profile == null || profile.trim().isEmpty()) {
            profile = "attendance";
        }
        String home = System.getProperty("user.home");
        Path pluginBin = Paths.get(home, ".dsh", "profiles", profile, "node_modules", ".bin", "crop-zoom");
        if (Files.isRegularFile(pluginBin) || Files.isExecutable(pluginBin)) {
            return pluginBin;
        }
        String plugin = properties.getHarness().getCropPlugin();
        if (plugin == null || plugin.trim().isEmpty()) {
            plugin = "dsh-crop-zoom";
        }
        Path mjs = Paths.get(home, ".dsh", "profiles", profile, "node_modules",
                plugin.trim(), "bin", "crop-zoom.mjs");
        if (Files.isRegularFile(mjs)) {
            return mjs;
        }
        return null;
    }

    /**
     * 按 CLI 契约切一批格，返回与 regions 同序的 PNG 路径。
     */
    public List<Path> crop(Path sourcePng, List<BBox> regions, Path outStemPng, int target, String mode)
            throws Exception {
        if (sourcePng == null || !Files.isRegularFile(sourcePng)) {
            throw new IllegalArgumentException("crop-zoom 源图不存在");
        }
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("crop-zoom regions 为空");
        }
        if (regions.size() > MAX_REGIONS) {
            throw new IllegalArgumentException("crop-zoom 单次最多 " + MAX_REGIONS + " 格");
        }
        Path bin = resolveBin();
        if (bin == null) {
            throw new IllegalStateException("crop-zoom CLI 未安装");
        }
        List<String> cmd = new ArrayList<String>();
        String binPath = bin.toAbsolutePath().toString();
        if (binPath.endsWith(".mjs") || binPath.endsWith(".js")) {
            cmd.add("node");
        }
        cmd.add(binPath);
        cmd.add("-i");
        cmd.add(sourcePng.toAbsolutePath().toString());
        for (int i = 0; i < regions.size(); i++) {
            BBox box = regions.get(i);
            cmd.add("-r");
            cmd.add(box.x + "," + box.y + "," + box.w + "," + box.h);
        }
        cmd.add("-t");
        cmd.add(String.valueOf(target > 0 ? target : 1600));
        cmd.add("-m");
        cmd.add(mode == null || mode.trim().isEmpty() ? "nearest" : mode.trim());
        cmd.add("--no-sheet");
        cmd.add("-o");
        cmd.add(outStemPng.toAbsolutePath().toString());
        cmd.add("--json");
        String out = run(cmd, TIMEOUT_MS);
        List<Path> files = parseFilePaths(out);
        if (files.size() != regions.size()) {
            throw new IllegalStateException("crop-zoom 输出数 " + files.size() + " != 请求 " + regions.size());
        }
        return files;
    }

    public static List<Path> parseFilePaths(String stdout) {
        List<Path> files = new ArrayList<Path>();
        JSONObject root = extractObject(stdout);
        if (root == null) {
            return files;
        }
        JSONArray arr = root.getJSONArray("files");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                String p = arr.getString(i);
                if (p != null && !p.trim().isEmpty()) {
                    files.add(Paths.get(p.trim()));
                }
            }
            return files;
        }
        JSONArray regions = root.getJSONArray("regions");
        if (regions == null) {
            return files;
        }
        for (int i = 0; i < regions.size(); i++) {
            JSONObject r = regions.getJSONObject(i);
            if (r == null) {
                continue;
            }
            String p = r.getString("outPath");
            if (p != null && !p.trim().isEmpty()) {
                files.add(Paths.get(p.trim()));
            }
        }
        return files;
    }

    /**
     * CLI 要求两边 ≥ 4px 且框完全落在图内。不合规则返回 null，由调用方走 Java 回退。
     */
    public static BBox clampForCli(BBox box, int imageW, int imageH) {
        if (box == null || imageW <= 0 || imageH <= 0) {
            return null;
        }
        int x = Math.max(0, box.x);
        int y = Math.max(0, box.y);
        if (x >= imageW || y >= imageH) {
            return null;
        }
        int w = Math.min(box.w, imageW - x);
        int h = Math.min(box.h, imageH - y);
        if (w < MIN_SIDE || h < MIN_SIDE) {
            return null;
        }
        return new BBox(x, y, w, h);
    }

    static JSONObject extractObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        Matcher m = JSON_OBJECT.matcher(text);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        if (last == null) {
            return null;
        }
        try {
            return JSON.parseObject(last);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String run(List<String> cmd, int timeoutMs) throws Exception {
        log.debug("crop-zoom {}", cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
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
            throw new IllegalStateException("crop-zoom timeout " + timeoutMs + "ms");
        }
        String out = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("crop-zoom exit " + process.exitValue() + ": " + out);
        }
        return out;
    }

    private static Path firstExistingFile(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (int i = 0; i < candidates.length; i++) {
            String raw = candidates[i];
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            Path p = Paths.get(expandHome(raw.trim()));
            if (Files.isRegularFile(p) || Files.isExecutable(p)) {
                return p;
            }
        }
        return null;
    }

    private static String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
