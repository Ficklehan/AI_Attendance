package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 按 MiMo / OpenAI 兼容 SSE 规范解析 chat.completions 流式响应。
 * <ul>
 *   <li>行格式：{@code data: {...}} 或 {@code data:{...}}，结束为 {@code data: [DONE]}</li>
 *   <li>仅拼接 {@code choices[0].delta.content} 增量，不使用 message 全量字段</li>
 * </ul>
 */
public final class MimoStreamParser {

    private static final Logger log = LoggerFactory.getLogger(MimoStreamParser.class);

    private MimoStreamParser() {
    }

    public static final class RoundResult {
        private final String roundText;
        private final String finishReason;

        public RoundResult(String roundText, String finishReason) {
            this.roundText = roundText != null ? roundText : "";
            this.finishReason = finishReason;
        }

        public String getRoundText() {
            return roundText;
        }

        public String getFinishReason() {
            return finishReason;
        }
    }

    /**
     * @param onDelta 每收到一段增量 content 时回调（可用于边收边解析）
     */
    public static RoundResult consume(InputStream inputStream, Consumer<String> onDelta) throws Exception {
        StringBuilder roundText = new StringBuilder();
        String finishReason = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    continue;
                }
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String data = trimmed.length() > 5 ? trimmed.substring(5).trim() : "";
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    if (finishReason == null) {
                        finishReason = "stop";
                    }
                    break;
                }

                try {
                    JSONObject parsed = JSON.parseObject(data);
                    JSONArray choices = parsed.getJSONArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        continue;
                    }
                    JSONObject choice = choices.getJSONObject(0);
                    String fr = choice.getString("finish_reason");
                    if (fr != null && !fr.isEmpty() && !"null".equalsIgnoreCase(fr)) {
                        finishReason = fr;
                    }
                    JSONObject delta = choice.getJSONObject("delta");
                    if (delta == null) {
                        continue;
                    }
                    appendDelta(delta.getString("content"), roundText, onDelta);
                    // reasoning_content 仅调试用，禁止拼入 roundText，否则会污染 JSON 行解析
                    if (log.isDebugEnabled()) {
                        String reasoning = delta.getString("reasoning_content");
                        if (reasoning != null && !reasoning.isEmpty()) {
                            log.debug("忽略 reasoning_content 增量 {} chars", reasoning.length());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 MiMo SSE chunk 失败: {}", truncate(data, 200), e);
                }
            }
        }

        return new RoundResult(roundText.toString(), finishReason);
    }

    private static void appendDelta(String token, StringBuilder roundText, Consumer<String> onDelta) {
        if (token == null || token.isEmpty()) {
            return;
        }
        roundText.append(token);
        if (onDelta != null) {
            onDelta.accept(token);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
