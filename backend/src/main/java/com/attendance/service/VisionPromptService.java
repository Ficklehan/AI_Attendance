package com.attendance.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.mapper.PluginConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 视觉识别提示词配置：把 VisionClient 里的 5 个硬编码 prompt 抽到 plugin_config 表，
 * 后台「AI 智能识别配置」可编辑、改完即生效（每次识别读库）。
 */
@Service
public class VisionPromptService {

    private static final String TYPE = "string";

    private final PluginConfigMapper pluginConfigMapper;

    public VisionPromptService(PluginConfigMapper pluginConfigMapper) {
        this.pluginConfigMapper = pluginConfigMapper;
    }

    /** 5 个视觉提示词的定义：key / 中文名 / 说明 / 默认值。 */
    private enum Spec {
        TABLE_BODY("vision.prompt.table_body", "整表读提示词",
                "识别考勤表主体：一次读取所有列所有行（行号/姓名/中介/班次/到达/离开/休息）。主路径使用。",
                "This is an attendance timesheet table body. Columns left-to-right: "
                        + "NO, NAME, AGENCY, SHIFT, ARRIVEE, DEPAR, PAUSE.\n"
                        + "Read every data row top-to-bottom, including empty rows.\n"
                        + "Output JSON only, no markdown:\n"
                        + "{\"rows\":[{\"no\":\"1\",\"name\":\"...\",\"struck\":false,\"agency\":\"...\","
                        + "\"shift\":\"10:00-19:00\",\"arrive\":\"10:00\",\"depart\":\"19:00\",\"pause\":\"60\"},...]}\n"
                        + "Use \"\" for empty cells. \"struck\" = the name is crossed out with no replacement written. "
                        + "Include every row, do not skip empty rows. Do not include the column header row in the output.\n"),
        HEADER("vision.prompt.header", "页头提示词",
                "识别表格上方的页头带：仓库名（warehouse）与打印日期（date_raw）。主路径使用。",
                "This image is the page header band of an attendance timesheet.\n"
                        + "Output JSON only, no markdown: {\"warehouse\":\"...\",\"date_raw\":\"...\"}\n"
                        + "warehouse = printed site/depot name (may be empty). "
                        + "date_raw = printed date exactly as written (may be empty).\n"),
        COLUMN_HEADERS("vision.prompt.column_headers", "表头列名提示词",
                "识别表头行的列名（左到右），用于按表头定位各列。兜底定列时使用。支持 {country} 占位符。",
                "This is a European attendance timesheet (country={country}).\n"
                        + "Read ONLY the printed column header row (the row of column titles above the data rows).\n"
                        + "Output JSON only, no markdown: {\"headers\":[\"...\",\"...\",...]}\n"
                        + "List the column titles from LEFT to RIGHT, one entry per column, in the order they appear.\n"
                        + "Use the exact printed text. If a column has no visible title, use \"\".\n"
                        + "Do NOT include page header (warehouse/date) text. Do NOT read data rows.\n"),
        CLOCK_COLUMN("vision.prompt.clock_column", "时钟列提示词",
                "识别单个时钟列（到达或离开）的手写 HH:MM，逐行读。整表读失败时的兜底。",
                "This image is ONE column of an attendance timesheet showing handwritten clock times "
                        + "(format HH:MM), one per data row, from top to bottom. There may be a printed column header "
                        + "near the top — ignore it. Read every data row in order.\n"
                        + "Output JSON only, no markdown: {\"times\":[\"HH:MM\",\"HH:MM\",...]}\n"
                        + "Use \"\" for an empty row. Include every row, do not skip empty rows.\n"),
        NAME_COLUMN("vision.prompt.name_column", "姓名列提示词",
                "识别姓名列的每行姓名，并判断是否被划线（struck）。整表读失败时的兜底。",
                "This image is ONE name column of an attendance timesheet (body rows only, no header row).\n"
                        + "List each body row top-to-bottom. For each row: the full name text, and whether the name "
                        + "is struck through with no replacement written.\n"
                        + "Output JSON only, no markdown: {\"names\":[{\"text\":\"...\",\"struck\":false},...]}\n"
                        + "Include every row, even if the name is empty. Do not invent names.\n");

        final String key;
        final String name;
        final String description;
        final String defaultValue;

        Spec(String key, String name, String description, String defaultValue) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.defaultValue = defaultValue;
        }
    }

    /** 读指定 key 的提示词；库中无值则返回默认值。 */
    public String getPrompt(String key) {
        Spec spec = specOf(key);
        String value = pluginConfigMapper.selectValue(key);
        return value == null || value.trim().isEmpty() ? spec.defaultValue : value;
    }

    /** 读表头列名提示词并替换 {country} 占位符。 */
    public String getColumnHeadersPrompt(String country) {
        return getPrompt(Spec.COLUMN_HEADERS.key).replace("{country}", country);
    }

    /** 配置页：返回 5 个提示词的当前值 + 默认值 + 说明。 */
    public JSONArray listAll() {
        JSONArray out = new JSONArray();
        for (Spec spec : Spec.values()) {
            JSONObject o = new JSONObject();
            o.put("key", spec.key);
            o.put("name", spec.name);
            o.put("description", spec.description);
            o.put("defaultValue", spec.defaultValue);
            String value = pluginConfigMapper.selectValue(spec.key);
            o.put("currentValue", value == null ? spec.defaultValue : value);
            out.add(o);
        }
        return out;
    }

    /** 保存（upsert）一批提示词。 */
    public void saveAll(JSONObject prompts) {
        for (Spec spec : Spec.values()) {
            Object v = prompts.get(spec.key);
            if (v == null) {
                continue;
            }
            pluginConfigMapper.upsertValue(spec.key, String.valueOf(v), TYPE, spec.description);
        }
    }

    private static Spec specOf(String key) {
        for (Spec spec : Spec.values()) {
            if (spec.key.equals(key)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("未知视觉提示词 key: " + key);
    }
}
