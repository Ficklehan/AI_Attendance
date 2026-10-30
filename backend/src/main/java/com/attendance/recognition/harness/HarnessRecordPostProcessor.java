package com.attendance.recognition.harness;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.service.NightShiftConfigService;
import com.attendance.util.NightShiftMarkSupport;
import com.attendance.util.RecordCountryDefaults;
import com.attendance.util.RecognizedDateNormalizer;
import com.attendance.util.RecognizedTimeNormalizer;
import com.attendance.util.RowReadabilitySupport;
import com.attendance.util.SignatureMarkResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Harness JSON 之后的业务处理：Pays、DATE_RAW 重装 Date、夜班、签字展示。不改识别值。
 */
@Component
public class HarnessRecordPostProcessor {

    private final NightShiftConfigService nightShiftConfigService;

    public HarnessRecordPostProcessor(NightShiftConfigService nightShiftConfigService) {
        this.nightShiftConfigService = nightShiftConfigService;
    }

    public List<JSONObject> apply(List<JSONArray> rows, String workingCountry, String promptCountry) {
        List<JSONObject> records = new ArrayList<JSONObject>();
        NightShiftConfigDTO night = nightShiftConfigService.getConfigForCountry(promptCountry);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject rec = toRecord(rows.get(i));
            RecordCountryDefaults.applyWorkingCountryPays(rec, workingCountry);
            rec.put("DATE_RAW", RecognizedDateNormalizer.sanitizeDateRaw(rec.getString("DATE_RAW")));
            rec.put("Date", RecognizedDateNormalizer.applyDateWithRaw(
                    rec.getString("Date"), rec.getString("DATE_RAW")));
            rec.put("ARRIVEE", legalClock(rec.getString("ARRIVEE")));
            rec.put("DEPAR", legalClock(rec.getString("DEPAR")));
            rec.put("_harnessUsed", Boolean.TRUE);
            RowReadabilitySupport.dropAbsentIfBlurred(rec);
            overlayNightShift(rec, night);
            resolveSignature(rec);
            records.add(rec);
        }
        return records;
    }

    static JSONObject toRecord(JSONArray row) {
        JSONObject n = new JSONObject();
        n.put("NO", cell(row, 0));
        n.put("Pays", cell(row, 1));
        n.put("Entrepot", cell(row, 2));
        n.put("Date", cell(row, 3));
        n.put("NOM_PRENOM", cell(row, 4));
        n.put("AGENCE_INTERIMAIRE", cell(row, 5));
        n.put("HORAIRES_DU_TRAVAIL", cell(row, 6));
        n.put("ARRIVEE", cell(row, 7));
        n.put("DEPAR", cell(row, 8));
        n.put("PAUSE", cell(row, 9));
        n.put("SIGNATURE", cell(row, 10));
        n.put("Observations", cell(row, 11));
        n.put("Mark", cell(row, 12));
        n.put("isDeleted", parseDeleted(cell(row, 13)));
        if (RecognizedDateNormalizer.isDateRawToken(cell(row, 14))
                && !RecognizedDateNormalizer.isDateRawToken(cell(row, 15))) {
            n.put("DATE_RAW", cell(row, 14));
            n.put("PAGE_NUM", cell(row, 15));
        } else {
            n.put("PAGE_NUM", cell(row, 14));
            n.put("DATE_RAW", cell(row, 15));
        }
        n.put("SmartMark", n.getString("Mark"));
        return n;
    }

    static String legalClock(String raw) {
        String t = RecognizedTimeNormalizer.normalizeClockTime(raw);
        return t == null ? "" : t;
    }

    static void overlayNightShift(JSONObject rec, NightShiftConfigDTO night) {
        String mark = rec.getString("Mark");
        if (mark != null && mark.contains("未出勤")) {
            return;
        }
        if (mark != null && mark.contains("模糊")) {
            return;
        }
        if (NightShiftMarkSupport.shouldMarkNightShift(
                rec.getString("ARRIVEE"), rec.getString("DEPAR"),
                rec.getString("HORAIRES_DU_TRAVAIL"), night)) {
            if (mark == null || !mark.contains("夜班")) {
                rec.put("Mark", appendMark(mark, "夜班"));
            }
        }
        rec.put("SmartMark", rec.getString("Mark"));
    }

    static void resolveSignature(JSONObject rec) {
        String source = rec.getString("SIGNATURE");
        String mark = SignatureMarkResolver.resolveFromAiOutput(
                source,
                rec.getBooleanValue("isDeleted"),
                rec.getString("SmartMark"),
                rec.getString("ARRIVEE"),
                rec.getString("DEPAR"),
                rec.getString("Mark"));
        rec.put("SIGNATURE", mark);
    }

    private static String appendMark(String mark, String token) {
        if (mark == null || mark.trim().isEmpty()) {
            return token;
        }
        return mark.trim() + ";" + token;
    }

    private static boolean parseDeleted(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
    }

    private static String cell(JSONArray row, int i) {
        if (row == null || i >= row.size() || row.get(i) == null) {
            return "";
        }
        return String.valueOf(row.get(i)).trim();
    }
}
