package com.attendance.service;

import java.io.IOException;

/**
 * MiMo HTTP 调用失败，携带状态码供 Key 故障切换判断。
 */
public class MimoApiException extends IOException {

    private final int statusCode;

    public MimoApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
