package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.HarnessRow;
import com.attendance.recognition.dto.HarnessSheet;
import com.attendance.recognition.dto.OcrLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Harness 最终回答：rows 契约；兼容旧 16 列数组流。
 */
public final class HarnessJsonParser {

    private static final Pattern ROW_MARK = Pattern.compile("<<<\\s*(\\d+)\\s*>>>");

    private HarnessJsonParser() {
    }

    public static HarnessSheet parseSheet(String raw) {
        HarnessSheet sheet = new HarnessSheet();
        if (raw == null || raw.trim().isEmpty()) {
            return sheet;
        }
        String text = stripFence(raw.trim());
        JSONObject root = extractObject(text);
        if (root != null && root.getJSONArray("rows") != null) {
            sheet.pageNum = str(root, "page_num", "pageNum", "PAGE_NUM");
            sheet.warehouse = str(root, "warehouse", "Entrepot");
            sheet.dateRaw = str(root, "date_raw", "dateRaw", "DATE_RAW");
            JSONArray rows = root.getJSONArray("rows");
            for (int i = 0; i < rows.size(); i++) {
                HarnessRow row = parseRow(rows.getJSONObject(i));
                if (row != null) {
                    sheet.rows.add(row);
                }
            }
            return sheet;
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            try {
                Object parsed = JSON.parse(text.substring(start, end + 1));
                if (parsed instanceof JSONArray) {
                    JSONArray arr = (JSONArray) parsed;
                    if (!arr.isEmpty() && arr.get(0) instanceof JSONObject) {
                        for (int i = 0; i < arr.size(); i++) {
                            HarnessRow row = parseRow(arr.getJSONObject(i));
                            if (row != null) {
                                sheet.rows.add(row);
                            }
                        }
                        if (!sheet.rows.isEmpty()) {
                            return sheet;
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall through to marked arrays
            }
        }
        return fromLegacyArrays(text, sheet);
    }

    /** @deprecated 仅测试旧流；正式路径用 {@link #parseSheet} */
    public static List<JSONArray> parseRows(String raw) {
        List<JSONArray> out = new ArrayList<JSONArray>();
        HarnessSheet sheet = parseSheet(raw);
        for (int i = 0; i < sheet.rows.size(); i++) {
            out.add(legacyArray(sheet.rows.get(i), sheet));
        }
        return out;
    }

    static HarnessRow parseRow(JSONObject o) {
        if (o == null) {
            return null;
        }
        HarnessRow row = new HarnessRow();
        row.rowNo = str(o, "row_no", "rowNo", "NO");
        row.no = line(o, "no", "NO");
        if (row.no.text.isEmpty() && !row.rowNo.isEmpty()) {
            row.no.text = row.rowNo;
        }
        row.name = line(o, "name", "NOM_PRENOM");
        row.nameCrossed = o.getBooleanValue("name_crossed") || o.getBooleanValue("nameCrossed");
        row.nameHasReplacement = o.getBooleanValue("name_has_replacement")
                || o.getBooleanValue("nameHasReplacement");
        row.agency = line(o, "agency", "AGENCE_INTERIMAIRE");
        row.shift = line(o, "shift", "HORAIRES_DU_TRAVAIL");
        row.arrivo = line(o, "arrivo", "ARRIVEE", "arrive");
        row.uscita = line(o, "uscita", "DEPAR", "depart");
        row.pausa = line(o, "pausa", "PAUSE");
        row.signature = line(o, "signature", "SIGNATURE");
        row.observations = line(o, "observations", "Observations");
        if (row.rowNo.isEmpty()) {
            row.rowNo = row.no.text;
        }
        return row;
    }

    static OcrLine line(JSONObject parent, String... keys) {
        OcrLine line = new OcrLine();
        if (parent == null || keys == null) {
            return line;
        }
        for (int i = 0; i < keys.length; i++) {
            Object raw = parent.get(keys[i]);
            if (raw == null) {
                continue;
            }
            if (raw instanceof JSONObject) {
                return parseLine((JSONObject) raw);
            }
            line.text = String.valueOf(raw).trim();
            return line;
        }
        return line;
    }

    static OcrLine parseLine(JSONObject o) {
        OcrLine line = new OcrLine();
        if (o == null) {
            return line;
        }
        if (o.get("text") != null) {
            line.text = String.valueOf(o.get("text")).trim();
        }
        if (o.containsKey("confidence")) {
            line.confidence = o.getDoubleValue("confidence");
        } else if (o.containsKey("conf")) {
            line.confidence = o.getDoubleValue("conf");
        }
        if (o.containsKey("empty")) {
            line.empty = Boolean.valueOf(o.getBooleanValue("empty"));
        }
        line.bbox = parseBBox(o.get("bbox"));
        if (line.bbox == null) {
            line.bbox = parseBBox(o);
        }
        return line;
    }

    static BBox parseBBox(Object raw) {
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            if (a.size() < 4) {
                return null;
            }
            int a0 = a.getIntValue(0);
            int a1 = a.getIntValue(1);
            int a2 = a.getIntValue(2);
            int a3 = a.getIntValue(3);
            if (a2 > a0 && a3 > a1) {
                return new BBox(a0, a1, a2 - a0, a3 - a1);
            }
            if (a2 > 0 && a3 > 0) {
                return new BBox(a0, a1, a2, a3);
            }
            return null;
        }
        if (raw instanceof JSONObject) {
            JSONObject o = (JSONObject) raw;
            if (o.containsKey("x1") || o.containsKey("x2")) {
                int x1 = o.getIntValue("x1");
                int y1 = o.getIntValue("y1");
                int x2 = o.getIntValue("x2");
                int y2 = o.getIntValue("y2");
                return new BBox(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
            }
            int w = o.containsKey("width") ? o.getIntValue("width") : o.getIntValue("w");
            int h = o.containsKey("height") ? o.getIntValue("height") : o.getIntValue("h");
            if (w > 0 && h > 0) {
                return new BBox(o.getIntValue("x"), o.getIntValue("y"), w, h);
            }
        }
        return null;
    }

    static JSONObject extractObject(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        try {
            JSONObject direct = JSON.parseObject(t);
            if (direct != null && direct.getJSONArray("rows") != null) {
                return direct;
            }
        } catch (Exception ignored) {
            // scan
        }
        int from = 0;
        while (from < t.length()) {
            int brace = t.indexOf('{', from);
            if (brace < 0) {
                return null;
            }
            int end = matchingBrace(t, brace);
            if (end < 0) {
                return null;
            }
            try {
                JSONObject o = JSON.parseObject(t.substring(brace, end + 1));
                if (o != null && o.getJSONArray("rows") != null) {
                    return o;
                }
            } catch (Exception ignored) {
                // next
            }
            from = brace + 1;
        }
        return null;
    }

    static HarnessSheet fromLegacyArrays(String text, HarnessSheet sheet) {
        Matcher m = ROW_MARK.matcher(text);
        boolean marked = false;
        while (m.find()) {
            marked = true;
            int bracket = text.indexOf('[', m.end());
            if (bracket < 0) {
                continue;
            }
            int end = matchingBracket(text, bracket);
            if (end < 0) {
                continue;
            }
            HarnessRow row = fromLegacyArray(parseArray(text.substring(bracket, end + 1)), sheet);
            if (row != null) {
                sheet.rows.add(row);
            }
        }
        if (marked || !sheet.rows.isEmpty()) {
            return sheet;
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return sheet;
        }
        try {
            Object parsed = JSON.parse(text.substring(start, end + 1));
            if (parsed instanceof JSONArray) {
                JSONArray arr = (JSONArray) parsed;
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONArray) {
                        HarnessRow row = fromLegacyArray((JSONArray) item, sheet);
                        if (row != null) {
                            sheet.rows.add(row);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return sheet;
    }

    static HarnessRow fromLegacyArray(JSONArray a, HarnessSheet sheet) {
        if (a == null || a.isEmpty()) {
            return null;
        }
        HarnessRow row = new HarnessRow();
        row.rowNo = cell(a, 0);
        row.no.text = cell(a, 0);
        row.name.text = cell(a, 4);
        row.agency.text = cell(a, 5);
        row.shift.text = cell(a, 6);
        fillClock(row.arrivo, cell(a, 7));
        fillClock(row.uscita, cell(a, 8));
        row.pausa.text = cell(a, 9);
        row.signature.text = cell(a, 10);
        row.observations.text = cell(a, 11);
        if (sheet != null) {
            if (sheet.warehouse == null || sheet.warehouse.isEmpty()) {
                sheet.warehouse = cell(a, 2);
            }
            if (sheet.pageNum == null || sheet.pageNum.isEmpty()) {
                sheet.pageNum = cell(a, 14);
            }
            if (sheet.dateRaw == null || sheet.dateRaw.isEmpty()) {
                sheet.dateRaw = cell(a, 15);
            }
        }
        return row;
    }

    static void fillClock(OcrLine line, String text) {
        line.text = text;
        if (text == null || text.trim().isEmpty()) {
            line.empty = Boolean.TRUE;
            line.confidence = 1;
        } else {
            line.empty = Boolean.FALSE;
            line.confidence = 0.9d;
        }
    }

    static String sheetDateFromLegacy(JSONArray a) {
        return cell(a, 15);
    }

    static JSONArray legacyArray(HarnessRow row, HarnessSheet sheet) {
        JSONArray a = new JSONArray();
        a.add(row.rowNo);
        a.add("");
        a.add(sheet.warehouse);
        a.add("");
        a.add(row.name.text);
        a.add(row.agency.text);
        a.add(row.shift.text);
        a.add(row.arrivo.text);
        a.add(row.uscita.text);
        a.add(row.pausa.text);
        a.add(row.signature.text);
        a.add(row.observations.text);
        a.add("");
        a.add(Boolean.FALSE);
        a.add(sheet.pageNum);
        a.add(sheet.dateRaw);
        return a;
    }

    static JSONArray parseArray(String json) {
        try {
            Object parsed = JSON.parse(json);
            if (parsed instanceof JSONArray) {
                return (JSONArray) parsed;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    static String stripFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int close = t.lastIndexOf("```");
            if (close > 0) {
                t = t.substring(0, close);
            }
        }
        return t.trim();
    }

    static int matchingBracket(String text, int startPos) {
        return matching(text, startPos, '[', ']');
    }

    static int matchingBrace(String text, int startPos) {
        return matching(text, startPos, '{', '}');
    }

    private static int matching(String text, int startPos, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escapeNext = false;
        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            if (c == '\\') {
                escapeNext = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == open) {
                    depth++;
                }
                if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static String str(JSONObject o, String... keys) {
        if (o == null || keys == null) {
            return "";
        }
        for (int i = 0; i < keys.length; i++) {
            String v = o.getString(keys[i]);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String cell(JSONArray row, int i) {
        if (row == null || i >= row.size() || row.get(i) == null) {
            return "";
        }
        return String.valueOf(row.get(i)).trim();
    }
}
