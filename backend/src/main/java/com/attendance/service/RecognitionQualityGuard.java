package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 检测大模型常见「编造表格」模式（与提示词示例 张三/李四 不同，多为按国家臆造的模板行）。
 */
@Component
public class RecognitionQualityGuard {

    private static final Pattern EXAMPLE_JSON_LINE = Pattern.compile(
            "^\\s*\\[\"[^\"]+\"(?:,\"[^\"]*\")+\\]\\s*$");

    private static final Set<String> TEMPLATE_SURNAMES = new HashSet<>(Arrays.asList(
            "DUPONT", "MARTIN", "BERNARD", "PETIT", "ROBERT",
            "DURAND", "MOREAU", "SIMON", "LAURENT", "LEFEBVRE",
            "GARCIA", "MULLER", "SCHMIDT", "SCHNEIDER"
    ));

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
                out.append("（输出时：禁止输出本段示例中的任何姓名、中介、时间；仅按图片单元格填写）\n");
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

    public boolean looksFabricated(List<JSONObject> records) {
        if (records == null || records.size() < 4) {
            return false;
        }
        int n = records.size();
        int score = 0;

        if (mostlySequentialNo(records, n)) {
            score++;
        }
        if (dominantSingleAgency(records, n)) {
            score++;
        }
        if (uniformPause(records, n)) {
            score++;
        }
        if (templateSurnameHits(records) >= Math.min(5, n)) {
            score++;
        }
        if (onlyRotatingShifts(records, n)) {
            score++;
        }
        return score >= 3;
    }

    public String describeFabricationReason(List<JSONObject> records) {
        StringBuilder sb = new StringBuilder("识别结果疑似模型编造而非读图：");
        if (mostlySequentialNo(records, records.size())) {
            sb.append("工号连续1,2,3…；");
        }
        if (dominantSingleAgency(records, records.size())) {
            sb.append("中介高度雷同；");
        }
        if (uniformPause(records, records.size())) {
            sb.append("休息分钟完全一致；");
        }
        if (templateSurnameHits(records) >= 5) {
            sb.append("姓名为常见模板法语名；");
        }
        if (onlyRotatingShifts(records, records.size())) {
            sb.append("班次呈 MATIN/SOIR/NUIT 机械轮换；");
        }
        sb.append("请换更清晰、完整的考勤表照片重试。");
        return sb.toString();
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
        if (unknownName >= n * 0.7 && mostlySequentialNo(records, n) && filledTimes >= n * 0.5) {
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

    private static boolean mostlySequentialNo(List<JSONObject> records, int n) {
        int hits = 0;
        for (int i = 0; i < n; i++) {
            String no = safe(records.get(i).getString("NO"));
            if (no.equals(String.valueOf(i + 1))
                    || no.equals(String.format("%02d", i + 1))
                    || no.equals(String.format("%03d", i + 1))
                    || no.equals(String.format("%04d", i + 1))) {
                hits++;
            }
        }
        return hits >= n * 0.75;
    }

    private static boolean dominantSingleAgency(List<JSONObject> records, int n) {
        Map<String, Integer> count = new HashMap<>();
        for (JSONObject r : records) {
            String agency = safe(r.getString("AGENCE_INTERIMAIRE"));
            if (agency.isEmpty()) {
                continue;
            }
            count.merge(agency, 1, Integer::sum);
        }
        if (count.isEmpty()) {
            return false;
        }
        int max = count.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return max >= n * 0.85;
    }

    private static boolean uniformPause(List<JSONObject> records, int n) {
        Set<String> pauses = new HashSet<>();
        for (JSONObject r : records) {
            Object p = r.get("PAUSE");
            if (p != null) {
                pauses.add(String.valueOf(p).trim());
            }
        }
        return pauses.size() == 1 && n >= 5;
    }

    private static int templateSurnameHits(List<JSONObject> records) {
        int hits = 0;
        for (JSONObject r : records) {
            String name = safe(r.getString("NOM_PRENOM")).toUpperCase();
            for (String surname : TEMPLATE_SURNAMES) {
                if (name.startsWith(surname + " ") || name.equals(surname)) {
                    hits++;
                    break;
                }
            }
        }
        return hits;
    }

    private static boolean onlyRotatingShifts(List<JSONObject> records, int n) {
        if (n < 6) {
            return false;
        }
        Set<String> allowed = new HashSet<>(Arrays.asList("MATIN", "SOIR", "NUIT", "MATIN ", "SOIR ", "NUIT "));
        int known = 0;
        for (JSONObject r : records) {
            String shift = safe(r.getString("HORAIRES_DU_TRAVAIL")).toUpperCase();
            if ("MATIN".equals(shift) || "SOIR".equals(shift) || "NUIT".equals(shift)) {
                known++;
            }
        }
        return known >= n * 0.9;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
