package com.attendance.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.util.SignatureMarkResolver;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 运行时处理 prompts.md 中的示例行，不修改配置文件本身。
 * 防止模型或解析逻辑把「示例」或「表头占位符」当成真实识别结果。
 */
@Component
public class RecognitionPromptGuard {

    /** 识别与续写共用，替代原【硬性要求】+ HANDWRITING_MARK_RULE 重复段落 */
    public static final String API_OUTPUT_CONSTRAINT = "\n\n【输出约束·API】\n"
            + "· 每行合法JSON数组；禁表头占位/照抄示例；看不清???/\"\"，禁编造凑行\n"
            + "· 先NO姓名再到离；名/工号???或空→到离必空\n"
            + "· SIGNATURE只填员工签名列(Firma等关键词列)单元格，禁表头字面量\n"
            + "· 15字段顺序与标记/PAGE_NUM规则以正文为准";

    private static final Set<String> EXAMPLE_KEYS = new HashSet<>(Arrays.asList(
            "1|张三", "2|李四", "3|王五",
            "1|John Smith", "2|Jane Doe", "3|Bob Wilson"
    ));

    private static final Set<String> HEADER_NAME_VALUES = new HashSet<>(Arrays.asList(
            "姓名", "nom_prenom", "nom et prénom", "name", "nom", "nominativo", "nombre"
    ));
    private static final Set<String> HEADER_AGENCY_VALUES = new HashSet<>(Arrays.asList(
            "供应商名称", "供应商", "agence_interimaire", "agence", "agency", "supplier", "intermediary"
    ));
    private static final Set<String> HEADER_SIGNATURE_VALUES = new HashSet<>(Arrays.asList(
            "员工签名", "signature", "firma", "signatura", "签名", "员工签"
    ));
    private static final Set<String> HEADER_OBSERVATION_VALUES = new HashSet<>(Arrays.asList(
            "备注", "observations", "remarks", "osservazioni", "observaciones"
    ));

    public String preparePromptForApi(String promptFromConfig) {
        return preparePromptForApi(promptFromConfig, null);
    }

    public String preparePromptForApi(String promptFromConfig, RecognitionQualityGuard qualityGuard) {
        if (promptFromConfig == null || promptFromConfig.trim().isEmpty()) {
            return promptFromConfig;
        }
        String base = promptFromConfig.trim();
        if (qualityGuard != null) {
            base = qualityGuard.preparePromptWithoutExamples(base);
        }
        return base + API_OUTPUT_CONSTRAINT;
    }

    public boolean isPromptExampleRecord(JSONObject record) {
        if (record == null) {
            return false;
        }
        if (isHeaderPlaceholderRecord(record)) {
            return true;
        }
        String no = safe(record.getString("NO"));
        String name = safe(record.getString("NOM_PRENOM"));
        if (EXAMPLE_KEYS.contains(no + "|" + name)) {
            return true;
        }
        if ("张三".equals(name) && "1".equals(no)) {
            return true;
        }
        if ("李四".equals(name) && "2".equals(no)) {
            return true;
        }
        if ("王五".equals(name) && "3".equals(no)) {
            return true;
        }
        String agency = safe(record.getString("AGENCE_INTERIMAIRE"));
        if ("???".equals(name) && "4".equals(no) && agency.contains("中介D")) {
            return true;
        }
        if (name.contains("张三") || name.contains("李四") || name.contains("王五")) {
            if (agency.contains("中介A") || agency.contains("中介B") || agency.contains("中介C")) {
                return true;
            }
        }
        return false;
    }

    public boolean isHeaderPlaceholderRecord(JSONObject record) {
        if (record == null) {
            return false;
        }
        String name = normalizeHeaderToken(record.getString("NOM_PRENOM"));
        String agency = normalizeHeaderToken(record.getString("AGENCE_INTERIMAIRE"));
        String signature = safe(record.getString("SIGNATURE"));
        String observations = normalizeHeaderToken(record.getString("Observations"));

        if (HEADER_NAME_VALUES.contains(name)) {
            return true;
        }
        String sigLower = signature.toLowerCase(Locale.ROOT);
        boolean sigHeader = HEADER_SIGNATURE_VALUES.contains(sigLower)
                || SignatureMarkResolver.isSignatureColumnHeader(signature);
        boolean agencyHeader = HEADER_AGENCY_VALUES.contains(agency);
        boolean obsHeader = HEADER_OBSERVATION_VALUES.contains(observations);
        // 仅当多个字段同时像表头时才判定为表头行，避免误杀「供应商名称」出现在数据列的情况
        int headerHits = 0;
        if (agencyHeader) headerHits++;
        if (obsHeader) headerHits++;
        if (sigHeader) headerHits++;
        if (headerHits >= 2) {
            return true;
        }
        if (agencyHeader && name.isEmpty()) {
            return true;
        }
        if (obsHeader && HEADER_NAME_VALUES.contains(name)) {
            return true;
        }
        if (sigHeader && HEADER_NAME_VALUES.contains(name)) {
            return true;
        }
        return false;
    }

    /** 模型原始回复疑似输出了表头占位符或非法 JSON 行 */
    public boolean looksLikeHeaderEcho(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        String text = raw.replace('\n', ' ');
        return (text.contains("姓名") && text.contains("供应商名称"))
                || (text.contains("NOM_PRENOM") && text.contains("AGENCE_INTERIMAIRE"))
                || (text.contains("员工签名") && text.contains("备注") && text.contains("["));
    }

    public boolean isPromptExampleArray(JSONArray row) {
        if (row == null || row.size() < 2) {
            return false;
        }
        JSONObject probe = probeFromArray(row);
        return isPromptExampleRecord(probe);
    }

    private static JSONObject probeFromArray(JSONArray row) {
        JSONObject probe = new JSONObject();
        probe.put("NO", cell(row, 0));
        if (row.size() >= 14) {
            probe.put("Pays", cell(row, 1));
            probe.put("Entrepot", cell(row, 2));
            probe.put("Date", cell(row, 3));
            probe.put("NOM_PRENOM", cell(row, 4));
            probe.put("AGENCE_INTERIMAIRE", cell(row, 5));
            probe.put("SIGNATURE", cell(row, 10));
            probe.put("Observations", cell(row, 11));
        } else {
            probe.put("NOM_PRENOM", cell(row, 1));
            if (row.size() > 2) {
                probe.put("AGENCE_INTERIMAIRE", cell(row, 2));
            }
        }
        return probe;
    }

    private static String cell(JSONArray row, int index) {
        return index < row.size() ? String.valueOf(row.get(index)) : "";
    }

    private static String normalizeHeaderToken(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
