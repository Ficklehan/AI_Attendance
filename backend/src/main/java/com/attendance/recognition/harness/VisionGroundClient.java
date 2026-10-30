package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.HarnessColumnCatalog;
import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.PageLayout;
import com.attendance.service.RecognitionModelRuntime;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * dsh-vision-router vision_ground：用现网 DeepSeek/MiMo 视觉接口在预览图上标格，坐标稍后映射回原图。
 */
@Component
public class VisionGroundClient {

    private static final Logger log = LoggerFactory.getLogger(VisionGroundClient.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*\\}");

    private final RecognitionModelRuntime runtime;
    private final RecognitionProperties properties;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public VisionGroundClient(RecognitionModelRuntime runtime, RecognitionProperties properties) {
        this.runtime = runtime;
        this.properties = properties;
    }

    public PageLayout ground(byte[] previewPng, String mime, int previewWidth, String countryCode, String apiKey)
            throws Exception {
        String prompt = buildPrompt(countryCode);
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(previewPng));
        imageUrl.put("detail", "high");
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", content);
        JSONArray messages = new JSONArray();
        messages.add(user);

        JSONObject body = new JSONObject();
        body.put("model", runtime.getModel());
        body.put("messages", messages);
        body.put("max_tokens", 8192);
        body.put("stream", Boolean.FALSE);
        body.put("temperature", 0);
        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);

        String raw = post(body, apiKey, properties.getHarness().getGroundTimeoutMs());
        return parseLayout(raw, previewWidth);
    }

    public PageLayout groundHeader(byte[] previewPng, String mime, int previewWidth, String apiKey)
            throws Exception {
        String prompt = buildHeaderPrompt();
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(previewPng));
        imageUrl.put("detail", "high");
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", content);
        JSONArray messages = new JSONArray();
        messages.add(user);
        JSONObject body = new JSONObject();
        body.put("model", runtime.getModel());
        body.put("messages", messages);
        body.put("max_tokens", 256);
        body.put("stream", Boolean.FALSE);
        body.put("temperature", 0);
        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);
        String raw = post(body, apiKey, Math.min(45000, properties.getHarness().getGroundTimeoutMs()));
        return parseLayout(raw, previewWidth);
    }

    public static String buildHeaderPrompt() {
        return "This is an attendance timesheet header. Output JSON only, no markdown.\n"
                + "Return: {\"warehouse\":\"\",\"dateRaw\":\"\",\"pageNum\":\"\",\"cells\":[]}\n"
                + "warehouse = printed site/depot name. dateRaw = printed date as written. "
                + "pageNum = printed page number or \"\". Do not list table cells. Do not guess times.";
    }

    public static String buildPrompt(String countryCode) {
        return "You locate table cells on an attendance timesheet. Output JSON only, no markdown.\n"
                + HarnessColumnCatalog.promptBlock(countryCode)
                + "Return: {\"warehouse\":\"\",\"dateRaw\":\"\",\"pageNum\":\"\","
                + "\"cells\":[{\"row\":1,\"field\":\"NAME|AGENCY|SHIFT|ARRIVEE|DEPAR|PAUSE|SIGNATURE|OBSERVATIONS|NO\","
                + "\"x\":0,\"y\":0,\"w\":0,\"h\":0}]}\n"
                + "Coordinates are pixels on THIS image, origin top-left. One object per body row per field.\n"
                + "Start at the first data row immediately below the printed column header. "
                + "List every body row through the last numbered or named row, including empty rows. "
                + "Do not start mid-table. Do not skip rows.\n"
                + "Do not read digit values. Do not guess times. Empty header fields use \"\".\n"
                + "Long diagonal strokes through clock columns are strikethrough, not a reason to skip those cells.";
    }

    public static PageLayout parseLayout(String raw, int previewWidth) {
        PageLayout layout = new PageLayout();
        layout.previewWidth = previewWidth;
        JSONObject root = extractObject(raw);
        if (root == null) {
            return layout;
        }
        layout.warehouse = emptyToBlank(root.getString("warehouse"));
        layout.dateRaw = emptyToBlank(root.getString("dateRaw"));
        layout.pageNum = emptyToBlank(root.getString("pageNum"));
        JSONArray cells = root.getJSONArray("cells");
        if (cells == null) {
            return layout;
        }
        for (int i = 0; i < cells.size(); i++) {
            JSONObject c = cells.getJSONObject(i);
            if (c == null) {
                continue;
            }
            String field = HarnessColumnCatalog.canonicalField(c.getString("field"));
            if (field.isEmpty()) {
                continue;
            }
            BBox box = parseBBox(c);
            if (box == null || box.w <= 0 || box.h <= 0) {
                continue;
            }
            layout.cells.add(new GroundedCell(c.getIntValue("row"), field, box));
        }
        return layout;
    }

    static BBox parseBBox(JSONObject c) {
        if (c == null) {
            return null;
        }
        JSONObject nested = c.getJSONObject("bbox");
        if (nested != null) {
            return parseBBox(nested);
        }
        if (c.containsKey("x1") || c.containsKey("x2")) {
            int x1 = c.getIntValue("x1");
            int y1 = c.getIntValue("y1");
            int x2 = c.getIntValue("x2");
            int y2 = c.getIntValue("y2");
            return new BBox(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
        int w = c.containsKey("width") ? c.getIntValue("width") : c.getIntValue("w");
        int h = c.containsKey("height") ? c.getIntValue("height") : c.getIntValue("h");
        return new BBox(c.getIntValue("x"), c.getIntValue("y"), w, h);
    }

    static JSONObject extractObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        try {
            JSONObject direct = JSON.parseObject(text);
            if (direct != null && (direct.containsKey("cells") || direct.containsKey("warehouse"))) {
                return direct;
            }
        } catch (Exception ignored) {
            // fall through
        }
        Matcher m = JSON_OBJECT.matcher(text);
        if (m.find()) {
            try {
                return JSON.parseObject(m.group());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String post(JSONObject body, String apiKey, int timeoutMs) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("视觉 API Key 未配置，无法 vision_ground");
        }
        OkHttpClient client = http.newBuilder()
                .readTimeout(Math.max(10, timeoutMs / 1000), TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(runtime.getApiUrl() + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey.trim())
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();
        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();
        String content = responseBody != null ? responseBody.string() : "";
        if (!response.isSuccessful()) {
            log.error("vision_ground HTTP {}: {}", response.code(), content);
            throw new IllegalStateException("vision_ground 失败: " + response.code());
        }
        JSONObject parsed = JSON.parseObject(content);
        if (parsed == null) {
            return "";
        }
        JSONArray choices = parsed.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
        return msg != null ? emptyToBlank(msg.getString("content")) : "";
    }

    private static String emptyToBlank(String s) {
        return s == null ? "" : s.trim();
    }
}
