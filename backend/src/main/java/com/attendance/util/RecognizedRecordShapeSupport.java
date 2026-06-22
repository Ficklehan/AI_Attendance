package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别结果行形态：粘连行修复、合并数组拆分、畸形行标记。
 */
public final class RecognizedRecordShapeSupport {

    public static final String PARSE_MALFORMED_KEY = "_parseMalformed";
    public static final String PARSE_MALFORMED_REASON_KEY = "_parseMalformedReason";
    public static final int EXPECTED_FIELD_COUNT = 15;
    /** @deprecated 使用 {@link com.attendance.dto.ImageQualityConfigDTO#getBlockMalformedRowPercent()} */
    @Deprecated
    public static final double MALFORMED_RATIO_FAIL_THRESHOLD = 0.10;

    private static final Set<String> KNOWN_COUNTRIES = new HashSet<>(Arrays.asList(
            "ITALIA", "ITALY", "FRANCE", "GERMANY", "DEUTSCHLAND", "NETHERLANDS",
            "SPAIN", "ESPANA", "ESPAÑA", "POLAND", "POLSKA", "CZECH", "CHINA"
    ));

    private static final Pattern VALID_NO = Pattern.compile("^\\d{1,4}$");
    private static final Pattern MERGED_BLOB = Pattern.compile(
            "^\\d{1,4}[A-Za-z]{4,}.*(?:false|true)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERGED_BLOB_SPLIT = Pattern.compile(
            "^(\\d{1,4})"
                    + "(ITALIA|ITALY|FRANCE|GERMANY|NETHERLANDS|SPAIN|POLAND|CHINA)"
                    + "([A-Z][A-Za-z]*)"
                    + "(\\d{8})"
                    + "(.+)"
                    + "(false|true)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_TOKEN = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final Pattern JAMMED_TIME_TAIL = Pattern.compile("^(.*?)(\\d{4})(\\d{4})(\\d{4})$");

    private RecognizedRecordShapeSupport() {
    }

    /** 在 {@link com.attendance.service.AIParserService#repairMissingRowClosingBrackets} 之后调用。 */
    public static String repairStickyRowBoundaries(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String repaired = peelLeadingUnquotedRows(text);
        repaired = repaired.replaceAll("(?i)(false|true)(?=\\s*\\[)", "$1]\n");
        repaired = repaired.replaceAll("(\"\")(?=\\s*\\[)", "$1]\n");
        return repaired;
    }

    private static String peelLeadingUnquotedRows(String text) {
        Pattern leading = Pattern.compile("(?:^|\\n)([^\\[\\n\\]]+?)(?=\\s*\\[\")");
        Matcher matcher = leading.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String blob = matcher.group(1).trim();
            JSONArray row = trySplitMergedBlob(blob);
            if (row != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("\n" + row.toJSONString() + "\n"));
                changed = true;
            }
        }
        if (!changed) {
            return text;
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    public static List<JSONArray> expandMergedRowArrays(JSONArray itemArray) {
        if (itemArray == null || itemArray.size() < 2) {
            return Collections.emptyList();
        }
        int size = itemArray.size();
        if (size == 16 && looksLikeMergedBlob(stringValue(itemArray.get(0)))
                && looksLikeValidNo(stringValue(itemArray.get(1)))) {
            List<JSONArray> rows = new ArrayList<>(2);
            JSONArray first = trySplitMergedBlob(stringValue(itemArray.get(0)));
            if (first != null) {
                rows.add(first);
            }
            JSONArray second = new JSONArray();
            for (int i = 1; i < 16; i++) {
                second.add(itemArray.get(i));
            }
            rows.add(second);
            return rows;
        }
        if (size > EXPECTED_FIELD_COUNT && size % EXPECTED_FIELD_COUNT == 0) {
            List<JSONArray> rows = new ArrayList<>();
            for (int offset = 0; offset < size; offset += EXPECTED_FIELD_COUNT) {
                JSONArray chunk = new JSONArray();
                for (int i = 0; i < EXPECTED_FIELD_COUNT; i++) {
                    chunk.add(itemArray.get(offset + i));
                }
                rows.add(chunk);
            }
            return rows;
        }
        return Collections.singletonList(itemArray);
    }

    public static JSONArray trySplitMergedBlob(String blob) {
        if (blob == null || blob.trim().isEmpty()) {
            return null;
        }
        String trimmed = blob.trim();
        Matcher matcher = MERGED_BLOB_SPLIT.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        String no = matcher.group(1);
        String pays = matcher.group(2).toUpperCase(Locale.ROOT);
        if ("ITALY".equals(pays)) {
            pays = "ITALIA";
        }
        String entrepot = matcher.group(3);
        String date = normalizeCompactOrStandardDate(matcher.group(4));
        String middle = matcher.group(5);
        boolean deleted = "true".equalsIgnoreCase(matcher.group(6));

        String arrive = "";
        String depart = "";
        String pause = "";
        String horaires = "";
        String agency = "";
        String name = middle;

        Matcher colonTimes = TIME_TOKEN.matcher(middle);
        List<String> times = new ArrayList<>();
        while (colonTimes.find()) {
            times.add(colonTimes.group());
        }
        if (!times.isEmpty()) {
            int firstTime = middle.indexOf(times.get(0));
            name = middle.substring(0, firstTime).trim();
            if (times.size() >= 1) {
                horaires = times.get(0) + (times.size() >= 2 ? "-" + times.get(1) : "");
            }
            if (times.size() >= 2) {
                arrive = times.get(times.size() >= 3 ? 1 : 0);
            }
            if (times.size() >= 3) {
                depart = times.get(2);
            } else if (times.size() == 2) {
                arrive = times.get(0);
                depart = times.get(1);
            }
        } else {
            Matcher jammed = JAMMED_TIME_TAIL.matcher(middle);
            if (jammed.matches()) {
                name = jammed.group(1).trim();
                arrive = toClock(jammed.group(2));
                depart = toClock(jammed.group(3));
                horaires = arrive + "-" + depart;
            }
        }

        String[] nameAgency = splitNameAgencyHeuristic(name);
        name = nameAgency[0];
        agency = nameAgency[1];

        JSONArray row = new JSONArray();
        row.add(no);
        row.add(pays);
        row.add(entrepot);
        row.add(date);
        row.add(name);
        row.add(agency);
        row.add(horaires);
        row.add(arrive);
        row.add(depart);
        row.add(pause);
        row.add("");
        row.add("");
        row.add("");
        row.add(deleted);
        row.add("");
        return row;
    }

    private static String normalizeCompactOrStandardDate(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.matches("\\d{8}")) {
            return trimmed.substring(0, 4) + "-" + trimmed.substring(4, 6) + "-" + trimmed.substring(6, 8);
        }
        return RecognizedDateNormalizer.normalizeDate(trimmed);
    }

    private static String[] splitNameAgencyHeuristic(String middle) {
        if (middle == null || middle.isEmpty()) {
            return new String[] {"", ""};
        }
        String[] knownAgencies = {"TEMPUS", "MANPOWER", "STARTPEOPLE", "STAFFMATCH", "JOB&TALENT", "ADECCO"};
        String upper = middle.toUpperCase(Locale.ROOT);
        for (String agency : knownAgencies) {
            int idx = upper.indexOf(agency);
            if (idx > 0) {
                return new String[] {
                        middle.substring(0, idx).trim(),
                        middle.substring(idx).trim()
                };
            }
            if (idx == 0) {
                return new String[] {"", middle.trim()};
            }
        }
        return new String[] {middle.trim(), ""};
    }

    private static String toClock(String hhmm) {
        if (hhmm == null || hhmm.length() != 4) {
            return "";
        }
        return hhmm.substring(0, 2) + ":" + hhmm.substring(2, 4);
    }

    public static boolean looksLikeMergedBlob(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 16) {
            return false;
        }
        if (VALID_NO.matcher(trimmed).matches()) {
            return false;
        }
        return MERGED_BLOB.matcher(trimmed).matches()
                || (trimmed.length() > 20 && TIME_TOKEN.matcher(trimmed).find()
                && KNOWN_COUNTRIES.stream().anyMatch(c -> trimmed.toUpperCase(Locale.ROOT).contains(c)));
    }

    public static boolean looksLikeValidNo(String value) {
        return value != null && VALID_NO.matcher(value.trim()).matches();
    }

    public static boolean isRawFieldCountValid(int count) {
        return count == 14 || count == EXPECTED_FIELD_COUNT;
    }

    public static boolean isNormalizedShapeMalformed(JSONObject record, int rawFieldCount) {
        if (record == null) {
            return true;
        }
        if (rawFieldCount > 0 && !isRawFieldCountValid(rawFieldCount)) {
            return true;
        }
        String no = safe(record.getString("NO"));
        if (looksLikeMergedBlob(no) || no.length() > 12) {
            return true;
        }
        String pays = safe(record.getString("Pays"));
        if (looksLikeValidNo(pays)) {
            return true;
        }
        String entrepot = safe(record.getString("Entrepot"));
        if (KNOWN_COUNTRIES.contains(entrepot.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    public static void markMalformed(JSONObject record, String reason) {
        if (record == null) {
            return;
        }
        record.put(PARSE_MALFORMED_KEY, true);
        if (reason != null && !reason.trim().isEmpty()) {
            record.put(PARSE_MALFORMED_REASON_KEY, reason.trim());
        }
    }

    public static double malformedRatio(List<JSONObject> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int malformed = 0;
        for (JSONObject record : records) {
            if (record != null && record.getBooleanValue(PARSE_MALFORMED_KEY)) {
                malformed++;
            }
        }
        return (double) malformed / records.size();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
