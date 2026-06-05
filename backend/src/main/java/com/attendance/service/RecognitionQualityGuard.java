package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 识别结果质量守卫：提示词示例照抄、看不清却臆测时间等可明确判定的情形。
 * 不再用连号工号/同一中介/统一休息/模板法语姓/班次轮换等业务常态做「编造」打分。
 */
@Component
public class RecognitionQualityGuard {

    private static final int MIN_EXAMPLE_ROWS_TO_REJECT = 3;

    private static final Pattern EXAMPLE_JSON_LINE = Pattern.compile(
            "^\\s*\\[\"[^\"]+\"(?:,\"[^\"]*\")+\\]\\s*$");

    @Autowired
    private RecognitionPromptGuard recognitionPromptGuard;

    /**
     * 发给 API 前去掉「示例」下的 JSON 行，仅保留规则；避免模型照抄或仿写示例结构。
     */
    public String preparePromptWithoutExamples(String promptFromConfig) {
        if (promptFromConfig == null || promptFromConfig.trim().isEmpty()) {
            return promptFromConfig;
        }
        StringBuilder out = new StringBuilder();
        boolean inExampleSection = false;
        for (String line : promptFromConfig.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.contains("示例") && !trimmed.startsWith("[")) {
                inExampleSection = true;
                out.append(line).append('\n');
                out.append("（输出时：禁止输出本段示例中的任何姓名、中介、时间；仅按图片单元格填写；看不清用 ??? 或 \"\"，严禁编造或猜测补全）\n");
                continue;
            }
            if (inExampleSection) {
                if (EXAMPLE_JSON_LINE.matcher(trimmed).matches()) {
                    continue;
                }
                if (trimmed.isEmpty()) {
                    inExampleSection = false;
                    out.append(line).append('\n');
                }
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    /**
     * 是否照抄了提示词中的示例行（张三/李四/王五等），解析阶段已过滤，此处作双保险。
     */
    public boolean looksFabricated(List<JSONObject> records) {
        if (records == null || records.size() < MIN_EXAMPLE_ROWS_TO_REJECT) {
            return false;
        }
        int exampleHits = 0;
        for (JSONObject record : records) {
            if (recognitionPromptGuard.isPromptExampleRecord(record)) {
                exampleHits++;
            }
        }
        return exampleHits >= MIN_EXAMPLE_ROWS_TO_REJECT;
    }

    public String describeFabricationReason(List<JSONObject> records) {
        return "识别结果含多条提示词示例行（非图中真实数据），请换更清晰、完整的考勤表照片重试。";
    }

    /**
     * 工号/姓名大量 ??? 或 ILLEGIBLE，但到达/离开却整齐填写 → 模型在猜时间而非读图。
     */
    public boolean looksUnreadableWithGuessedTimes(List<JSONObject> records) {
        if (records == null || records.size() < 3) {
            return false;
        }
        int n = records.size();
        int unknownIdentity = 0;
        int unknownNo = 0;
        int unknownName = 0;
        int filledTimes = 0;
        for (JSONObject r : records) {
            String no = safe(r.getString("NO"));
            String name = safe(r.getString("NOM_PRENOM"));
            boolean uNo = isUnknown(no);
            boolean uName = isUnknown(name);
            if (uNo) {
                unknownNo++;
            }
            if (uName) {
                unknownName++;
            }
            if (uNo && uName) {
                unknownIdentity++;
            }
            if (hasFilledTime(r)) {
                filledTimes++;
            }
        }
        if (unknownIdentity >= n * 0.6 && filledTimes >= n * 0.5) {
            return true;
        }
        return unknownNo >= n * 0.7 && unknownName >= n * 0.5 && filledTimes >= n * 0.6;
    }

    public String describeUnreadableReason(List<JSONObject> records) {
        return "表格大量工号/姓名为 ??? 或 ILLEGIBLE，但到达/离开时间却被整齐填写，疑似模型臆测而非读图。请换更清晰照片重试。";
    }

    private static boolean isUnknown(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String t = value.trim();
        return "???".equals(t)
                || "??".equals(t)
                || "unknown".equalsIgnoreCase(t)
                || "illegible".equalsIgnoreCase(t);
    }

    private static boolean hasFilledTime(JSONObject r) {
        return !isUnknown(safe(r.getString("ARRIVEE"))) || !isUnknown(safe(r.getString("DEPAR")));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
