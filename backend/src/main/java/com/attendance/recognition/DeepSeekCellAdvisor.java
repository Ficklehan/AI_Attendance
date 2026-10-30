package com.attendance.recognition;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.dto.CellEvidence;
import com.attendance.service.RecognitionModelRuntime;
import com.attendance.util.RecognizedTimeNormalizer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 有墨低分格：只把本格切图发给 Flash，禁止邻行/班次。
 */
@Component
public class DeepSeekCellAdvisor {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekCellAdvisor.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*\\}");

    public static final String CELL_PROMPT =
            "This image is ONE attendance clock cell (arrive or leave).\n"
                    + "A long slash is strikethrough, not blank and not void. Treat the line as transparent and read digits under it.\n"
                    + "Output JSON only: {\"time\":\"HH:MM\" or \"\",\"illegible\":true|false}\n"
                    + "Pure white / printed colon only → time \"\" and illegible false.\n"
                    + "Ink present but not a complete HH:MM → time \"\" and illegible true.\n"
                    + "Do not guess from shift, neighbor rows, or common work hours. Look only at this crop.";

    private final RecognitionModelRuntime runtime;
    private final RecognitionProperties properties;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build();

    public DeepSeekCellAdvisor(RecognitionModelRuntime runtime, RecognitionProperties properties) {
        this.runtime = runtime;
        this.properties = properties;
    }

    public void advise(CellEvidence evidence, BufferedImage crop, String apiKey) throws Exception {
        if (evidence == null || crop == null) {
            return;
        }
        byte[] png;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(crop, "png", buf);
        png = buf.toByteArray();

        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", CELL_PROMPT);
        content.add(textPart);
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
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

        String raw = post(body, apiKey);
        apply(evidence, raw);
    }

    public static void apply(CellEvidence evidence, String raw) {
        JSONObject obj = extract(raw);
        if (obj == null) {
            evidence.lowConfidence = true;
            evidence.source = "harness";
            evidence.putDiag("flashCalled", Boolean.TRUE);
            evidence.putDiag("flashRaw", preview(raw, 400));
            evidence.putDiag("flashParse", "fail");
            return;
        }
        boolean illegible = obj.getBooleanValue("illegible");
        String rawTime = obj.getString("time");
        String time = RecognizedTimeNormalizer.normalizeClockTime(rawTime);
        if (time == null) {
            time = "";
        }
        evidence.source = "harness";
        evidence.confidence = -1;
        evidence.putDiag("flashRaw", preview(raw, 400));
        evidence.putDiag("flashTimeRaw", rawTime == null ? "" : rawTime);
        evidence.putDiag("flashTime", time);
        evidence.putDiag("flashIllegible", Boolean.valueOf(illegible));
        evidence.putDiag("flashCalled", Boolean.TRUE);
        if (!time.isEmpty()) {
            evidence.text = time;
            evidence.lowConfidence = false;
        } else {
            evidence.text = "";
            evidence.lowConfidence = illegible || evidence.hasInk || evidence.inkAfterLineRemoval;
        }
    }

    static String preview(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String one = raw.replace('\r', ' ').replace('\n', ' ').trim();
        if (one.length() <= max) {
            return one;
        }
        return one.substring(0, max) + "...";
    }

    static JSONObject extract(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        try {
            JSONObject direct = JSON.parseObject(text);
            if (direct != null && (direct.containsKey("time") || direct.containsKey("illegible"))) {
                return direct;
            }
        } catch (Exception ignored) {
            // continue
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

    private String post(JSONObject body, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("视觉 API Key 未配置，无法读格");
        }
        int timeoutMs = properties.getHarness().getVlmTimeoutMs();
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
            log.warn("cell advisor HTTP {}: {}", response.code(), content);
            throw new IllegalStateException("读格失败: " + response.code());
        }
        JSONObject parsed = JSON.parseObject(content);
        JSONArray choices = parsed != null ? parsed.getJSONArray("choices") : null;
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
        return msg != null ? msg.getString("content") : "";
    }
}
