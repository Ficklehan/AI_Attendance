package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.config.MimoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecognitionSupport {

    public static final String MSG_MIMO_NOT_CONFIGURED = ErrorKeys.MIMO_NOT_CONFIGURED;

    @Autowired
    private MimoProperties mimoProperties;

    @Value("${attendance.allow-simulated-recognition:false}")
    private boolean allowSimulatedRecognition;

    public boolean isMimoConfigured() {
        String apiKey = mimoProperties.getApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public boolean allowSimulatedFallback() {
        return allowSimulatedRecognition;
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
