package com.attendance.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 单次识别全链路跟踪：写入 SLF4J 日志，并序列化供任务 anomaly_summary / 小程序拉取排查。
 */
public class RecognitionTrace {

    private static final Logger log = LoggerFactory.getLogger("RecognitionTrace");

    private final String taskId;
    private final String client;
    private final List<JSONObject> steps = new ArrayList<>();
    private Consumer<JSONObject> stepListener;

    public RecognitionTrace(String taskId, String client) {
        this.taskId = taskId != null ? taskId : "unknown";
        this.client = client != null ? client : "unknown";
    }

    public void setStepListener(Consumer<JSONObject> listener) {
        this.stepListener = listener;
    }

    public void step(String phase, JSONObject detail) {
        JSONObject entry = new JSONObject();
        entry.put("at", Instant.now().toString());
        entry.put("phase", phase);
        entry.put("detail", detail != null ? detail : new JSONObject());
        steps.add(entry);
        log.info("[识别跟踪][{}][{}] {} {}", taskId, client, phase,
                detail != null ? detail.toJSONString() : "{}");
        if (stepListener != null) {
            try {
                stepListener.accept(entry);
            } catch (Exception ignored) {
                // SSE 客户端断开等
            }
        }
    }

    public void step(String phase, String key, Object value) {
        JSONObject detail = new JSONObject();
        detail.put(key, value);
        step(phase, detail);
    }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("taskId", taskId);
        root.put("client", client);
        root.put("steps", new JSONArray(steps));
        return root;
    }

    public static String preview(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\r', ' ').replace('\n', ' ');
        return oneLine.length() <= maxLen ? oneLine : oneLine.substring(0, maxLen) + "...";
    }

    public static JSONArray sampleRecords(List<com.alibaba.fastjson.JSONObject> records, int max) {
        JSONArray arr = new JSONArray();
        if (records == null) {
            return arr;
        }
        int n = Math.min(max, records.size());
        for (int i = 0; i < n; i++) {
            com.alibaba.fastjson.JSONObject r = records.get(i);
            JSONObject s = new JSONObject();
            s.put("NO", r.getString("NO"));
            s.put("NOM_PRENOM", r.getString("NOM_PRENOM"));
            s.put("Date", r.getString("Date"));
            s.put("ARRIVEE", r.getString("ARRIVEE"));
            s.put("DEPAR", r.getString("DEPAR"));
            arr.add(s);
        }
        return arr;
    }
}
