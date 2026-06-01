package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.config.MimoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecognitionSupport {

    public static final String MSG_MIMO_NOT_CONFIGURED = ErrorKeys.MIMO_NOT_CONFIGURED;

    @Autowired
    private MimoProperties mimoProperties;

    public boolean isMimoConfigured() {
        String apiKey = mimoProperties.getApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public boolean allowSimulatedFallback() {
        String flag = System.getProperty("ALLOW_SIMULATED_RECOGNITION");
        if (flag == null || flag.isBlank()) {
            flag = System.getenv("ALLOW_SIMULATED_RECOGNITION");
        }
        return "true".equalsIgnoreCase(flag != null ? flag.trim() : "");
    }

    public void requireRealAi() {
        if (isMimoConfigured()) {
            return;
        }
        if (allowSimulatedFallback()) {
            return;
        }
        throw new BusinessException(ErrorCode.AI_PARSE_ERROR, MSG_MIMO_NOT_CONFIGURED);
    }

    public boolean shouldUseSimulatedRecognition() {
        return !isMimoConfigured() && allowSimulatedFallback();
    }
}
