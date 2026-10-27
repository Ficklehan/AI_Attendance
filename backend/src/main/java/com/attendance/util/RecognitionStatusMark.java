package com.attendance.util;

import com.alibaba.fastjson.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 识别状态只认模型第13位标记，不用到达/离开空值推断未出勤。
 * 展示优先级：模糊 → 未出勤 → 缺卡 → 正常。
 */
public final class RecognitionStatusMark {

    public static final String BLUR = "模糊";
    public static final String ABSENT = "未出勤";
    public static final String NORMAL = "正常";
    public static final String HANDWRITING = "手写";
    public static final String MISSING_PUNCH = "缺卡";

    public static final Set<String> MODEL_MARK_TOKENS = new HashSet<>(Arrays.asList(
            HANDWRITING, BLUR, NORMAL, ABSENT, MISSING_PUNCH
    ));

    private static final List<String> BLUR_TOKENS = Arrays.asList(
            BLUR, "blur", "borroso", "unscharf", "wazig", "rozmazan", "nieostry"
    );
    private static final List<String> ABSENT_TOKENS = Arrays.asList(
            ABSENT, "absent", "ausente", "abwesend", "afwezig", "nieobecny", "nepřítom"
    );
    private static final List<String> MISSING_PUNCH_TOKENS = Arrays.asList(
            MISSING_PUNCH, "到达缺卡", "离开缺卡", "到达缺位", "离开缺位", "缺位"
    );

    private RecognitionStatusMark() {
    }

    public static boolean containsBlur(String mark) {
        return containsAny(mark, BLUR_TOKENS);
    }

    public static boolean containsAbsent(String mark) {
        return containsAny(mark, ABSENT_TOKENS);
    }

    public static boolean containsMissingPunch(String mark) {
        if (mark == null || mark.trim().isEmpty()) {
            return false;
        }
        if (containsAny(mark, MISSING_PUNCH_TOKENS)) {
            return true;
        }
        String lower = mark.toLowerCase(Locale.ROOT);
        return lower.contains("缺位") || lower.contains("缺卡");
    }

    /** 历史「缺位」及到达/离开缺卡一律归一为「缺卡」。 */
    public static String canonicalizeToken(String mark) {
        if (mark == null) {
            return "";
        }
        String token = mark.trim();
        if (token.isEmpty()) {
            return "";
        }
        if (containsMissingPunch(token)) {
            return MISSING_PUNCH;
        }
        return token;
    }

    /**
     * 未出勤只看标记。含模糊则不是未出勤；禁止用到离皆空覆盖模型标记。
     */
    public static boolean isAbsentStatus(JSONObject record) {
        if (record == null) {
            return false;
        }
        if (Boolean.TRUE.equals(record.getBoolean("isDeleted"))) {
            return false;
        }
        if (Boolean.TRUE.equals(record.getBoolean("_manuallyAdded"))) {
            return false;
        }
        if (Boolean.TRUE.equals(record.getBoolean("_restored"))) {
            return false;
        }
        String combined = joinMarks(record.getString("SmartMark"), record.getString("Mark"));
        if (containsBlur(combined)) {
            return false;
        }
        return containsAbsent(combined);
    }

    static String joinMarks(String smartMark, String mark) {
        String a = smartMark == null ? "" : smartMark.trim();
        String b = mark == null ? "" : mark.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty() || a.equals(b)) {
            return a;
        }
        return a + ";" + b;
    }

    private static boolean containsAny(String text, List<String> tokens) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
