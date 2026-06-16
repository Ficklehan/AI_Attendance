package com.attendance.util;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;

import java.util.Locale;

/**
 * 识别失败信息归一化为 i18n messageKey，避免把 MiMo 原始 HTTP 响应直接展示给用户。
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
        if (lower.contains("mimo_api_key") || lower.contains("mimo api key") || trimmed.contains("未配置 MIMO")) {
            return ErrorKeys.MIMO_NOT_CONFIGURED;
        }
        if (isMimoTransientFailure(lower)) {
            return ErrorKeys.MIMO_UNAVAILABLE;
        }
        return trimmed;
    }

    private static boolean isMimoTransientFailure(String lower) {
        return lower.contains("xiaomimimo.com")
                || lower.contains("api请求失败")
                || lower.contains("502")
                || lower.contains("503")
                || lower.contains("504")
                || lower.contains("429")
                || lower.contains("bad gateway")
                || lower.contains("service unavailable")
                || lower.contains("gateway timeout");
    }
}
