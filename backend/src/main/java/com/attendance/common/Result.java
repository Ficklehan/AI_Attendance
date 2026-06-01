package com.attendance.common;

import java.util.Collections;
import java.util.Map;

public class Result<T> {
    private int code;
    private String message;
    private String messageKey;
    private Map<String, Object> messageArgs;
    private T data;
    private long timestamp;

    public Result() {
    }

    public Result(int code, String message, T data, long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public Map<String, Object> getMessageArgs() {
        return messageArgs;
    }

    public void setMessageArgs(Map<String, Object> messageArgs) {
        this.messageArgs = messageArgs;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null, System.currentTimeMillis());
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data, System.currentTimeMillis());
    }

    public static <T> Result<T> success(T data, String message) {
        return new Result<>(200, message, data, System.currentTimeMillis());
    }

    public static <T> Result<T> error(int code, String messageKey, Map<String, Object> messageArgs) {
        Result<T> result = new Result<>(code, messageKey, null, System.currentTimeMillis());
        result.setMessageKey(messageKey);
        result.setMessageArgs(messageArgs != null ? messageArgs : Collections.emptyMap());
        result.setMessage(messageKey);
        return result;
    }

    public static <T> Result<T> error(BusinessException e) {
        return error(e.getCode(), e.getMessageKey(), e.getMessageArgs());
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null, System.currentTimeMillis());
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>(code, message, null, System.currentTimeMillis());
        if (message != null && message.startsWith("errors.")) {
            result.setMessageKey(message);
        }
        return result;
    }
}