package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;

import java.util.Locale;

/**
 * 识别失败信息归一化为 i18n messageKey，避免把 AI 原始 HTTP 响应直接展示给用户。
 */
public final class RecognitionFailureMessages {

    private RecognitionFailureMessages() {
    }

    public static String toClientMessage(Throwable error) {
        if (error == null) {
            return ErrorKeys.REQUEST_FAILED;
        }
        if (error instanceof BusinessException) {
            BusinessException be = (BusinessException) error;
            if (be.getMessageKey() != null && !be.getMessageKey().trim().isEmpty()) {
                return be.getMessageKey().trim();
            }
        }
        String msg = error.getMessage() != null ? error.getMessage() : "识别失败";
        return toClientMessage(msg);
    }

    public static String toClientMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return ErrorKeys.REQUEST_FAILED;
        }
        String trimmed = msg.trim();
        if (trimmed.startsWith("errors.")) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.contains("deepseek api key") || lower.contains("deepseek 未配置")) {
            return ErrorKeys.DEEPSEEK_NOT_CONFIGURED;
        }
        if (lower.contains("mimo_api_key") || lower.contains("mimo api key") || trimmed.contains("未配置 MIMO")) {
            return ErrorKeys.MIMO_NOT_CONFIGURED;
        }
        if (isDeepSeekInvalidModel(lower)) {
            return ErrorKeys.DEEPSEEK_INVALID_MODEL;
        }
        if (lower.contains("deepseek.com") || lower.contains("deepseek")) {
            if (isTransientFailure(lower)) {
                return ErrorKeys.DEEPSEEK_UNAVAILABLE;
            }
            if (lower.contains("api请求失败") || lower.contains("api request failed")) {
                return ErrorKeys.DEEPSEEK_UNAVAILABLE;
            }
        }
        if (lower.contains("xiaomimimo.com") || lower.contains("xiaomi mimo")) {
            if (isTransientFailure(lower) || lower.contains("api请求失败")) {
                return ErrorKeys.MIMO_UNAVAILABLE;
            }
        }
        if (lower.contains("api请求失败") || lower.contains("api request failed")) {
            if (isTransientFailure(lower)) {
                return ErrorKeys.AI_UNAVAILABLE;
            }
            return ErrorKeys.AI_UNAVAILABLE;
        }
        if (isTransientFailure(lower)) {
            return ErrorKeys.AI_UNAVAILABLE;
        }
        if (trimmed.matches("\\d+")) {
            return ErrorKeys.REQUEST_FAILED;
        }
        return trimmed;
    }

    private static boolean isDeepSeekInvalidModel(String lower) {
        return lower.contains("supported api model names")
                || (lower.contains("invalid_request") && lower.contains("model"))
                || lower.contains("but you passed");
    }

    private static boolean isTransientFailure(String lower) {
        return lower.contains("502")
                || lower.contains("503")
                || lower.contains("504")
                || lower.contains("429")
                || lower.contains("bad gateway")
                || lower.contains("service unavailable")
                || lower.contains("gateway timeout");
    }
}
