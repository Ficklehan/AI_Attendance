package com.attendance.service;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * 识别链路可重试错误判定（网络抖动、流中断、5xx 等）。
 */
public final class RecognitionRetrySupport {

    public static final long[] RETRY_DELAYS_MS = {5000L, 15000L, 45000L};
    public static final int MAX_STREAM_ATTEMPTS = 3;

    private RecognitionRetrySupport() {
    }

    public static boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof InterruptedIOException
                || error instanceof SocketTimeoutException
                || error instanceof SocketException
                || error instanceof TimeoutException) {
            return true;
        }
        if (error instanceof IOException) {
            String msg = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
            if (msg.contains("stream") || msg.contains("reset") || msg.contains("timeout")
                    || msg.contains("connection") || msg.contains("broken pipe")
                    || msg.contains("unexpected end")) {
                return true;
            }
        }
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("stream_incomplete") || message.contains("识别超时")) {
            return true;
        }
        if (message.contains("502") || message.contains("503") || message.contains("429")
                || message.contains("bad gateway") || message.contains("service unavailable")) {
            return true;
        }
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            return isRetryable(cause);
        }
        return false;
    }

    public static void sleepBeforeRetry(int attemptIndex) throws InterruptedException {
        int idx = Math.max(0, Math.min(attemptIndex, RETRY_DELAYS_MS.length - 1));
        Thread.sleep(RETRY_DELAYS_MS[idx]);
    }
}
