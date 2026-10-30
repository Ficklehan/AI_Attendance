package com.attendance.recognition;

import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.recognition.dto.CellEvidence;
import com.attendance.recognition.dto.OcrRawResult;
import com.attendance.util.NightShiftMarkSupport;
import com.attendance.util.RecognizedDateNormalizer;
import com.attendance.util.RecognizedTimeNormalizer;
import com.attendance.util.RecognitionStatusMark;
import com.attendance.util.SignatureMarkResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 像素巡检仪口径的纯 Java 判定。禁止调模型。禁止到离皆空推断未出勤（必须无墨）。
 */
@Component
public class AttendanceRuleEngine {

    private final RecognitionProperties properties;

    public AttendanceRuleEngine(RecognitionProperties properties) {
        this.properties = properties;
    }

    public JSONObject apply(OcrRawResult raw, String workingCountry, NightShiftConfigDTO nightShift) {
        JSONObject record = new JSONObject();
        RecognitionProperties.Rule rule = properties.getRule();
        boolean nameDeleted = raw.nameStruck && !raw.nameHasReplacement
                && !raw.arrive.hasInk && !raw.depart.hasInk;
        boolean rowDeleted = raw.rowDeleted || nameDeleted;

        String arrive = legalOrEmpty(raw.arrive);
        String depart = legalOrEmpty(raw.depart);
        boolean arriveInk = raw.arrive.inkAfterLineRemoval || raw.arrive.hasInk;
        boolean departInk = raw.depart.inkAfterLineRemoval || raw.depart.hasInk;
        boolean arriveLegal = arrive.matches("\\d{2}:\\d{2}");
        boolean departLegal = depart.matches("\\d{2}:\\d{2}");

        List<String> marks = new ArrayList<String>();
        if (rowDeleted) {
            arrive = "";
            depart = "";
            marks.add(RecognitionStatusMark.ABSENT);
            record.put("isDeleted", Boolean.TRUE);
        } else if (needsBlur(raw.arrive, arriveLegal, arriveInk, rule)
                || needsBlur(raw.depart, departLegal, departInk, rule)) {
            if (needsBlur(raw.arrive, arriveLegal, arriveInk, rule)) {
                if (!rule.isBlurKeepText() || !arriveLegal) {
                    arrive = "";
                }
            }
            if (needsBlur(raw.depart, departLegal, departInk, rule)) {
                if (!rule.isBlurKeepText() || !departLegal) {
                    depart = "";
                }
            }
            marks.add(RecognitionStatusMark.BLUR);
            record.put("isDeleted", Boolean.FALSE);
        } else if (!arriveInk && !departInk) {
            arrive = "";
            depart = "";
            marks.add(RecognitionStatusMark.ABSENT);
            record.put("isDeleted", Boolean.FALSE);
        } else if (arriveLegal ^ departLegal) {
            marks.add(RecognitionStatusMark.MISSING_PUNCH);
            record.put("isDeleted", Boolean.FALSE);
        } else {
            marks.add(RecognitionStatusMark.NORMAL);
            record.put("isDeleted", Boolean.FALSE);
        }

        if (marks.contains(RecognitionStatusMark.BLUR)) {
            marks.remove(RecognitionStatusMark.ABSENT);
        }

        record.put("_markWhy", markWhy(rowDeleted, raw.arrive, raw.depart,
                arriveLegal, departLegal, arriveInk, departInk, rule));
        record.put("_arriveSource", raw.arrive.source);
        record.put("_departSource", raw.depart.source);

        String pause = normalizePause(raw.pauseText);
        String shift = RecognizedTimeNormalizer.normalizeShiftSchedule(raw.shift);
        if (shift == null) {
            shift = raw.shift == null ? "" : raw.shift;
        }

        if (!marks.contains(RecognitionStatusMark.ABSENT)
                && !marks.contains(RecognitionStatusMark.BLUR)
                && NightShiftMarkSupport.shouldMarkNightShift(arrive, depart, shift, nightShift)) {
            marks.add("夜班");
        }

        record.put("NO", raw.noText == null ? "" : raw.noText);
        record.put("NOM_PRENOM", sanitizeName(raw.name));
        record.put("AGENCE_INTERIMAIRE", raw.agency == null ? "" : raw.agency.trim());
        record.put("HORAIRES_DU_TRAVAIL", shift);
        record.put("ARRIVEE", arrive);
        record.put("DEPAR", depart);
        record.put("PAUSE", pause);
        record.put("Observations", raw.observations == null ? "" : raw.observations.trim());
        record.put("PAGE_NUM", raw.pageNum == null ? "" : raw.pageNum);
        record.put("DATE_RAW", RecognizedDateNormalizer.sanitizeDateRaw(raw.headerDateRaw));
        record.put("Date", RecognizedDateNormalizer.applyDateWithRaw("", record.getString("DATE_RAW")));
        record.put("Entrepot", raw.headerWarehouse == null ? "" : raw.headerWarehouse.trim());
        record.put("Mark", join(marks));
        record.put("SmartMark", join(marks));

        String signatureSource = raw.signatureHasInk ? "已签字" : (raw.signatureText == null ? "" : raw.signatureText);
        if (raw.signatureHasInk && (raw.signatureText == null || raw.signatureText.trim().isEmpty())) {
            signatureSource = "已签字";
        }
        String signatureMark = SignatureMarkResolver.resolveFromAiOutput(
                signatureSource,
                record.getBooleanValue("isDeleted"),
                record.getString("SmartMark"),
                arrive,
                depart,
                record.getString("Mark"));
        record.put("SIGNATURE", signatureMark);
        putGeom(record, "arrivo", raw.arrive);
        putGeom(record, "uscita", raw.depart);
        if (workingCountry != null) {
            record.put("_workingCountry", workingCountry);
        }
        if ("harness".equals(raw.arrive.source) || "harness".equals(raw.depart.source)) {
            record.put("_harnessUsed", Boolean.TRUE);
        }
        if (raw.arrive.inkAfterLineRemoval && !arriveLegal || raw.depart.inkAfterLineRemoval && !departLegal) {
            record.put("_reviewReason", "clock_ink");
        }
        return record;
    }

    static boolean needsBlur(CellEvidence cell, boolean legal, boolean ink, RecognitionProperties.Rule rule) {
        if (!ink) {
            return false;
        }
        if (!legal) {
            return true;
        }
        if (cell.lowConfidence) {
            return true;
        }
        if (cell.confidence >= 0 && cell.confidence < rule.getAcceptThreshold()) {
            return true;
        }
        return false;
    }

    static String markWhy(boolean rowDeleted, CellEvidence arrive, CellEvidence depart,
                          boolean arriveLegal, boolean departLegal,
                          boolean arriveInk, boolean departInk, RecognitionProperties.Rule rule) {
        if (rowDeleted) {
            return "name_struck_no_clock";
        }
        String arriveWhy = cellWhy(arrive, arriveLegal, arriveInk, rule);
        String departWhy = cellWhy(depart, departLegal, departInk, rule);
        boolean blur = needsBlur(arrive, arriveLegal, arriveInk, rule)
                || needsBlur(depart, departLegal, departInk, rule);
        if (blur) {
            return "blur:ARRIVEE=" + arriveWhy + ";DEPAR=" + departWhy;
        }
        if (!arriveInk && !departInk) {
            return "no_ink_both";
        }
        if (arriveLegal ^ departLegal) {
            return "one_sided:ARRIVEE=" + arriveWhy + ";DEPAR=" + departWhy;
        }
        return "both_legal";
    }

    static String cellWhy(CellEvidence cell, boolean legal, boolean ink, RecognitionProperties.Rule rule) {
        if (!ink) {
            return "no_ink";
        }
        if (!legal) {
            return "ink_no_legal";
        }
        if (cell != null && cell.lowConfidence) {
            return "low_conf";
        }
        if (cell != null && cell.confidence >= 0 && cell.confidence < rule.getAcceptThreshold()) {
            return "conf_" + round2(cell.confidence) + "<" + round2(rule.getAcceptThreshold());
        }
        return "legal";
    }

    private static String round2(double v) {
        return String.format(java.util.Locale.US, "%.2f", Double.valueOf(v));
    }

    static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replace("?", "").replace("？", "").replaceAll("\\s+", " ").trim();
    }

    /** 原提示词 §5：只取清晰数字，丢掉 min 后缀；叠行拼出的 6060/690 置空。 */
    static String normalizePause(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        String first = text.split("\\s+")[0]
                .replaceAll("(?i)minutes?", "")
                .replaceAll("(?i)mins?", "")
                .replaceAll("(?i)mn", "");
        Matcher matcher = Pattern.compile("(\\d{1,3})").matcher(first);
        if (!matcher.find()) {
            Matcher any = Pattern.compile("(\\d{1,3})").matcher(text);
            if (!any.find()) {
                return "";
            }
            matcher = any;
        }
        int value = Integer.parseInt(matcher.group(1));
        if (value > 180) {
            return "";
        }
        return String.valueOf(value);
    }

    static void putGeom(JSONObject record, String prefix, CellEvidence cell) {
        if (record == null || prefix == null) {
            return;
        }
        if (cell == null) {
            record.put("_" + prefix + "Confidence", null);
            return;
        }
        record.put("_" + prefix + "Confidence", Double.valueOf(cell.confidence));
        if (cell.bbox != null) {
            com.alibaba.fastjson.JSONArray box = new com.alibaba.fastjson.JSONArray();
            box.add(Integer.valueOf(cell.bbox.x));
            box.add(Integer.valueOf(cell.bbox.y));
            box.add(Integer.valueOf(cell.bbox.w));
            box.add(Integer.valueOf(cell.bbox.h));
            record.put("_" + prefix + "Bbox", box);
        }
    }

    static String legalOrEmpty(CellEvidence cell) {
        if (cell == null) {
            return "";
        }
        String raw = cell.text;
        String normalized;
        if ("ocr".equals(cell.source) && !RecognizedTimeNormalizer.hasClockSeparator(raw)) {
            normalized = "";
        } else if ("ocr".equals(cell.source)) {
            normalized = RecognizedTimeNormalizer.normalizeHandwrittenClock(raw);
        } else {
            normalized = RecognizedTimeNormalizer.normalizeClockTime(raw);
        }
        if (normalized == null || !normalized.matches("\\d{2}:\\d{2}")) {
            return "";
        }
        return normalized;
    }

    private static String join(List<String> marks) {
        if (marks == null || marks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < marks.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(marks.get(i));
        }
        return sb.toString();
    }
}
