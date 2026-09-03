package com.attendance.common;

public class ErrorCode {
    public static final int USER_NOT_FOUND = 1001;
    public static final int USER_ALREADY_EXISTS = 1002;
    public static final int PASSWORD_ERROR = 1003;
    public static final int TOKEN_INVALID = 1004;
    public static final int TOKEN_EXPIRED = 1005;
    public static final int PERMISSION_DENIED = 1006;
    public static final int PARAM_ERROR = 1007;

    public static final int TASK_NOT_FOUND = 2001;
    public static final int TASK_STATUS_ERROR = 2002;

    public static final int FILE_UPLOAD_ERROR = 3001;
    public static final int FILE_TYPE_NOT_ALLOWED = 3002;
    public static final int FILE_SIZE_EXCEEDED = 3003;

    public static final int AI_PARSE_ERROR = 4001;
    public static final int FEISHU_API_ERROR = 4002;

    private ErrorCode() {}
}
