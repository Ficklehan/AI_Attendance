package com.attendance.recognition;

import com.attendance.recognition.dto.CellOcrResult;
import com.attendance.util.RecognizedTimeNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 OCR 多行里挑印刷列/时刻，不盲目取第一行。
 */
public final class OcrLinePicker {

    private OcrLinePicker() {
    }

    public static String clock(CellOcrResult ocr) {
        List<String> parts = parts(ocr);
        for (int i = 0; i < parts.size(); i++) {
            String n = RecognizedTimeNormalizer.normalizeHandwrittenClock(parts.get(i));
            if (isLegal(n)) {
                return n;
            }
        }
        return "";
    }

    public static String shift(CellOcrResult ocr) {
        List<String> parts = parts(ocr);
        String best = "";
        int bestTokens = 0;
        for (int i = 0; i < parts.size(); i++) {
            String line = parts.get(i);
            if (!hasDigit(line) || mostlyCjk(line)) {
                continue;
            }
            int tokens = timeMarks(line);
            if (tokens > bestTokens) {
                bestTokens = tokens;
                best = line;
            }
        }
        if (best.isEmpty()) {
            return "";
        }
        String n = RecognizedTimeNormalizer.normalizeShiftSchedule(best);
        return n == null ? best.trim() : n.trim();
    }

    public static String name(CellOcrResult ocr) {
        List<String> latin = new ArrayList<String>();
        List<String> parts = parts(ocr);
        for (int i = 0; i < parts.size(); i++) {
            String line = parts.get(i).trim();
            if (line.isEmpty() || mostlyCjk(line) || latinLetters(line) < 2) {
                continue;
            }
            latin.add(line);
        }
        if (latin.isEmpty()) {
            return "";
        }
        List<String> unique = new ArrayList<String>();
        for (int i = 0; i < latin.size(); i++) {
            String line = latin.get(i);
            if (unique.isEmpty() || !sameNameToken(unique.get(unique.size() - 1), line)) {
                unique.add(line);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < unique.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(unique.get(i));
        }
        return foldDoubledName(sb.toString().replaceAll("\\s+", " ").trim());
    }

    static boolean sameNameToken(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.replaceAll("\\s+", "").equalsIgnoreCase(b.replaceAll("\\s+", ""));
    }

    static String foldDoubledName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String[] toks = name.split(" ");
        if (toks.length >= 2 && toks.length % 2 == 0) {
            int half = toks.length / 2;
            boolean dup = true;
            for (int i = 0; i < half; i++) {
                if (!toks[i].equalsIgnoreCase(toks[i + half])) {
                    dup = false;
                    break;
                }
            }
            if (dup) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < half; i++) {
                    if (i > 0) {
                        sb.append(' ');
                    }
                    sb.append(toks[i]);
                }
                return sb.toString();
            }
        }
        return name;
    }

    public static String agency(CellOcrResult ocr) {
        String best = "";
        int bestLen = 0;
        List<String> parts = parts(ocr);
        for (int i = 0; i < parts.size(); i++) {
            String line = parts.get(i).trim();
            if (line.isEmpty() || mostlyCjk(line) || latinLetters(line) < 2) {
                continue;
            }
            if (line.length() > bestLen) {
                bestLen = line.length();
                best = line;
            }
        }
        return best;
    }

    public static String number(CellOcrResult ocr) {
        return number(ocr, -1);
    }

    /** 行号：多候选时取最接近表体行号的，避免 13 被先读成 1。 */
    public static String number(CellOcrResult ocr, int expected) {
        List<String> cands = new ArrayList<String>();
        List<String> parts = parts(ocr);
        for (int i = 0; i < parts.size(); i++) {
            String line = parts.get(i).trim();
            if (line.matches("\\d{1,3}")) {
                cands.add(line);
            }
        }
        if (cands.isEmpty()) {
            String digits = digitsOnly(ocr == null ? "" : ocr.primaryDigitLine());
            return digits.matches("\\d{1,3}") ? digits : "";
        }
        if (cands.size() == 1 || expected <= 0) {
            return longestNumber(cands);
        }
        String best = cands.get(0);
        int bestDist = Math.abs(parseInt(best) - expected);
        for (int i = 1; i < cands.size(); i++) {
            int dist = Math.abs(parseInt(cands.get(i)) - expected);
            if (dist < bestDist
                    || (dist == bestDist && cands.get(i).length() > best.length())) {
                bestDist = dist;
                best = cands.get(i);
            }
        }
        return best;
    }

    private static String longestNumber(List<String> cands) {
        String best = cands.get(0);
        for (int i = 1; i < cands.size(); i++) {
            if (cands.get(i).length() > best.length()) {
                best = cands.get(i);
            }
        }
        return best;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<String> parts(CellOcrResult ocr) {
        List<String> out = new ArrayList<String>();
        if (ocr == null) {
            return out;
        }
        if (ocr.lines != null) {
            for (int i = 0; i < ocr.lines.size(); i++) {
                String line = ocr.lines.get(i);
                if (line != null && !line.trim().isEmpty()) {
                    out.add(line.trim());
                }
            }
        }
        if (ocr.text != null && !ocr.text.trim().isEmpty()) {
            out.add(ocr.text.trim());
        }
        return out;
    }

    private static boolean isLegal(String clock) {
        return clock != null && clock.matches("\\d{2}:\\d{2}");
    }

    private static int timeMarks(String s) {
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':' || c == 'h' || c == 'H') {
                n++;
            }
        }
        return n;
    }

    private static boolean hasDigit(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    private static boolean mostlyCjk(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int cjk = 0;
        int letters = latinLetters(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x3040 && c <= 0x30FF || c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            }
        }
        return cjk > 0 && cjk >= letters;
    }

    private static int latinLetters(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= 0x00C0 && c <= 0x024F) {
                n++;
            }
        }
        return n;
    }

    private static String digitsOnly(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
