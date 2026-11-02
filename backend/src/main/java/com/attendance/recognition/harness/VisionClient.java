package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.DeepSeekProperties;
import com.attendance.config.MimoProperties;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直连 DeepSeek 视觉接口读大字：定位时钟列粗框、读姓名列、读页头。
 * 只做“认列名/读大字”这类低分辨率够用的活，不做精确像素定位（交给 TableGridDetector）。
 */
@Component
public class VisionClient {

    private static final Logger log = LoggerFactory.getLogger(VisionClient.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*\\}");

    private final RecognitionModelRuntime runtime;
    private final DeepSeekProperties deepSeekProperties;
    private final MimoProperties mimoProperties;
    private final AtomicInteger keyCursor = new AtomicInteger();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public VisionClient(RecognitionModelRuntime runtime, DeepSeekProperties deepSeekProperties,
                        MimoProperties mimoProperties) {
        this.runtime = runtime;
        this.deepSeekProperties = deepSeekProperties;
        this.mimoProperties = mimoProperties;
    }

    /**
     * 读表头行的列名（左到右）。只读印刷表头文字，不要求坐标——坐标由投影法给出。
     * 返回列名列表；失败返回 null。
     */
    public List<String> readColumnHeaders(BufferedImage image, String countryCode) throws Exception {
        String code = countryCode == null || countryCode.trim().isEmpty() ? "FR" : countryCode.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("This is a European attendance timesheet (country=").append(code).append(").\n");
        sb.append("Read ONLY the printed column header row (the row of column titles above the data rows).\n");
        sb.append("Output JSON only, no markdown: {\"headers\":[\"...\",\"...\",...]}\n");
        sb.append("List the column titles from LEFT to RIGHT, one entry per column, in the order they appear.\n");
        sb.append("Use the exact printed text. If a column has no visible title, use \"\".\n");
        sb.append("Do NOT include page header (warehouse/date) text. Do NOT read data rows.\n");
        String raw = complete(image, sb.toString(), 512);
        JSONObject root = extractObject(raw);
        if (root == null || root.getJSONArray("headers") == null) {
            return null;
        }
        JSONArray arr = root.getJSONArray("headers");
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < arr.size(); i++) {
            Object v = arr.get(i);
            out.add(v == null ? "" : String.valueOf(v).trim());
        }
        log.info("readColumnHeaders country={} headers={}", code, out);
        return out.isEmpty() ? null : out;
    }

    /** 读姓名列：返回 [{name, struck}] 数组。 */
    public JSONArray describeNameColumn(BufferedImage crop) throws Exception {
        String prompt = "This image is ONE name column of an attendance timesheet (body rows only, no header row).\n"
                + "List each body row top-to-bottom. For each row: the full name text, and whether the name is struck through with no replacement written.\n"
                + "Output JSON only, no markdown: {\"names\":[{\"text\":\"...\",\"struck\":false},...]}\n"
                + "Include every row, even if the name is empty. Do not invent names.\n";
        String raw = complete(crop, prompt, 2048);
        JSONObject root = extractObject(raw);
        if (root == null || root.getJSONArray("names") == null) {
            return null;
        }
        return root.getJSONArray("names");
    }

    /** 读页头：返回 {warehouse, date_raw}。 */
    public String[] describeHeader(BufferedImage crop) throws Exception {
        String prompt = "This image is the page header band of an attendance timesheet.\n"
                + "Output JSON only, no markdown: {\"warehouse\":\"...\",\"date_raw\":\"...\"}\n"
                + "warehouse = printed site/depot name (may be empty). date_raw = printed date exactly as written (may be empty).\n";
        String raw = complete(crop, prompt, 256);
        JSONObject root = extractObject(raw);
        if (root == null) {
            return new String[] {"", ""};
        }
        return new String[] {str(root, "warehouse"), str(root, "date_raw", "dateRaw")};
    }

    /** 读时钟列：crop 整列放大 2x，逐行读手写 HH:MM，返回 times 列表（空行 ""）；失败返回 null。 */
    public List<String> readClockColumn(BufferedImage columnCrop) throws Exception {
        BufferedImage zoomed = scaleBy(columnCrop, 2.0);
        String prompt = "This image is ONE column of an attendance timesheet showing handwritten clock times "
                + "(format HH:MM), one per data row, from top to bottom. There may be a printed column header "
                + "near the top — ignore it. Read every data row in order.\n"
                + "Output JSON only, no markdown: {\"times\":[\"HH:MM\",\"HH:MM\",...]}\n"
                + "Use \"\" for an empty row. Include every row, do not skip empty rows.\n";
        String raw = complete(zoomed, prompt, 1024);
        JSONObject root = extractObject(raw);
        if (root == null || root.getJSONArray("times") == null) {
            return null;
        }
        JSONArray arr = root.getJSONArray("times");
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < arr.size(); i++) {
            Object v = arr.get(i);
            out.add(v == null ? "" : String.valueOf(v).trim());
        }
        return out;
    }

    /**
     * 整表读：crop 表体（所有列 × 表体行）用 JPEG 原尺寸直传，一次读每行的 no/name/struck/agency/shift/arrive/depart/pause。
     * 返回 rows JSONArray；失败返回 null。
     */
    public JSONArray readTableBody(BufferedImage tableCrop) throws Exception {
        String prompt = "This is an attendance timesheet table body. Columns left-to-right: "
                + "NO, NAME, AGENCY, SHIFT, ARRIVEE, DEPAR, PAUSE.\n"
                + "The first row may be the column header — skip it. Read every data row top-to-bottom.\n"
                + "Output JSON only, no markdown:\n"
                + "{\"rows\":[{\"no\":\"1\",\"name\":\"...\",\"struck\":false,\"agency\":\"...\","
                + "\"shift\":\"10:00-19:00\",\"arrive\":\"10:00\",\"depart\":\"19:00\",\"pause\":\"60\"},...]}\n"
                + "Use \"\" for empty cells. \"struck\" = the name is crossed out with no replacement written. "
                + "Include every row, do not skip empty rows.\n";
        BufferedImage zoomed = scaleBy(tableCrop, 1.5);
        String raw = complete(zoomed, prompt, 4096, "jpeg");
        JSONObject root = extractObject(raw);
        if (root == null || root.getJSONArray("rows") == null) {
            return null;
        }
        return root.getJSONArray("rows");
    }

    private static BufferedImage scaleBy(BufferedImage src, double factor) {
        int w = (int) Math.round(src.getWidth() * factor);
        int h = (int) Math.round(src.getHeight() * factor);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private String complete(BufferedImage image, String prompt, int maxTokens) throws Exception {
        return complete(image, prompt, maxTokens, "png");
    }

    private String complete(BufferedImage image, String prompt, int maxTokens, String format) throws Exception {
        byte[] bytes = toBytes(image, format);
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(bytes));
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
        body.put("max_tokens", maxTokens);
        body.put("stream", Boolean.FALSE);
        body.put("temperature", 0);
        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);

        String key = nextKey();
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("识别引擎 API Key 未配置，无法调用视觉");
        }
        Request request = new Request.Builder()
                .url(runtime.getApiUrl() + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + key.trim())
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();
        Response response = http.newCall(request).execute();
        ResponseBody responseBody = response.body();
        String contentStr = responseBody != null ? responseBody.string() : "";
        if (!response.isSuccessful()) {
            log.error("vision HTTP {}: {}", response.code(), contentStr);
            throw new IllegalStateException("vision 调用失败: " + response.code());
        }
        JSONObject parsed = JSON.parseObject(contentStr);
        if (parsed == null) {
            return "";
        }
        JSONArray choices = parsed.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
        return msg != null ? str(msg, "content") : "";
    }

    private String nextKey() {
        if (runtime.isMimoEngine()) {
            return nextKeyFrom(mimoProperties.getResolvedApiKeys(), mimoProperties.getApiKey());
        }
        return nextKeyFrom(deepSeekProperties.getResolvedApiKeys(), deepSeekProperties.getApiKey());
    }

    private String nextKeyFrom(List<String> keys, String singleKey) {
        if (keys != null && !keys.isEmpty()) {
            int n = keys.size();
            int i = (keyCursor.getAndIncrement() & Integer.MAX_VALUE) % n;
            String k = keys.get(i);
            return k == null ? "" : k.trim();
        }
        return singleKey == null ? "" : singleKey.trim();
    }

    static JSONObject extractObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        try {
            JSONObject direct = JSON.parseObject(text);
            if (direct != null) {
                return direct;
            }
        } catch (Exception ignored) {
            // scan
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

    private static String str(JSONObject o, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            Object v = o.get(keys[i]);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    return s;
                }
            }
        }
        return "";
    }

    private static byte[] toBytes(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(image, format, buf);
        return buf.toByteArray();
    }
}
