package com.attendance.common;

import java.util.Collections;
import java.util.Map;

public class BusinessException extends RuntimeException {
    private final int code;
    private final String messageKey;
    private final Map<String, Object> messageArgs;

    public BusinessException(int code, String messageKey) {
        this(code, messageKey, Collections.emptyMap());
    }

    public BusinessException(int code, String messageKey, Map<String, Object> messageArgs) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs != null ? messageArgs : Collections.emptyMap();
    }

    public int getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Map<String, Object> getMessageArgs() {
        return messageArgs;
    }
}
