package com.attendance.common;

/**
 * i18n message keys returned to clients; PC / miniprogram translate via locale files.
 */
public final class ErrorKeys {

    public static final String LOGIN_REQUIRED = "errors.loginRequired";
    public static final String ACCESS_DENIED = "errors.accessDenied";
    public static final String ADMIN_REQUIRED = "errors.adminRequired";
    public static final String EXPORT_JOB_NOT_FOUND = "errors.exportJobNotFound";
    public static final String EXPORT_JOB_NOT_READY = "errors.exportJobNotReady";
    public static final String USER_NOT_FOUND = "errors.userNotFound";
    public static final String USER_DISABLED = "errors.userDisabled";
    public static final String CANNOT_DISABLE_SELF = "errors.cannotDisableSelf";
    public static final String CANNOT_DELETE_SELF = "errors.cannotDeleteSelf";
    public static final String CANNOT_DELETE_LAST_ADMIN = "errors.cannotDeleteLastAdmin";
    public static final String USER_ALREADY_EXISTS = "errors.userAlreadyExists";
    public static final String EMAIL_ALREADY_EXISTS = "errors.emailAlreadyExists";
    public static final String FEISHU_USER_ALREADY_EXISTS = "errors.feishuUserAlreadyExists";
    public static final String PASSWORD_WRONG = "errors.passwordWrong";
    public static final String OLD_PASSWORD_WRONG = "errors.oldPasswordWrong";

    public static final String RECOGNITION_CONCURRENT_LIMIT = "errors.recognitionConcurrentLimit";

    public static final String TASK_NOT_FOUND = "errors.taskNotFound";
    public static final String TASK_STATUS_CANNOT_CONFIRM = "errors.taskStatusCannotConfirm";
    public static final String FEISHU_RETRY_CONFIRMED_ONLY = "errors.feishuRetryConfirmedOnly";
    public static final String FEISHU_ALREADY_SYNCED = "errors.feishuAlreadySynced";
    public static final String FEISHU_SYNC_DISABLED = "errors.feishuSyncDisabled";
    public static final String NO_CONFIRMED_DATA_TO_SYNC = "errors.noConfirmedDataToSync";
    public static final String CONFIRMED_DATA_EMPTY = "errors.confirmedDataEmpty";
    public static final String CONFIRM_REQUIRED_FIELDS_MISSING = "errors.confirmRequiredFieldsMissing";
    public static final String CONFIRMED_TASK_CANNOT_DELETE = "errors.confirmedTaskCannotDelete";
    public static final String DELETE_REASON_REQUIRED = "errors.deleteReasonRequired";
    public static final String TASK_DELETE_CONFIRMED_PERMISSION_DENIED = "errors.taskDeleteConfirmedPermissionDenied";
    public static final String TASK_ALREADY_CANCELLED = "errors.taskAlreadyCancelled";
    public static final String CONFIRMED_TASK_CANNOT_CANCEL = "errors.confirmedTaskCannotCancel";
    public static final String TASK_NOT_CONFIRMED = "errors.taskNotConfirmed";
    public static final String CALIBRATE_REASON_REQUIRED = "errors.calibrateReasonRequired";
    public static final String CALIBRATE_RECORD_NOT_FOUND = "errors.calibrateRecordNotFound";
    public static final String CALIBRATE_NO_CHANGES = "errors.calibrateNoChanges";
    public static final String CALIBRATE_PERMISSION_DENIED = "errors.calibratePermissionDenied";
    public static final String TASK_ACCESS_DENIED = "errors.taskAccessDenied";
    public static final String FILE_ACCESS_DENIED = "errors.fileAccessDenied";
    public static final String NO_EXPORT_DATA = "errors.noExportData";

    public static final String FILE_NOT_FOUND = "errors.fileNotFound";
    public static final String IMAGE_INVALID = "errors.imageInvalid";
    public static final String INVALID_FILE_PATH = "errors.invalidFilePath";
    public static final String UPLOAD_IMAGE_TOO_SMALL = "errors.uploadImageTooSmall";
    public static final String IMAGES_ONLY = "errors.imagesOnly";
    public static final String UNRECOGNIZED_IMAGE_FORMAT = "errors.unrecognizedImageFormat";
    public static final String PDF_TOO_MANY_PAGES = "errors.pdfTooManyPages";
    public static final String PDF_EMPTY = "errors.pdfEmpty";
    public static final String PDF_CONVERT_FAILED = "errors.pdfConvertFailed";

    public static final String MIMO_NOT_CONFIGURED = "errors.mimoNotConfigured";
    public static final String MIMO_UNAVAILABLE = "errors.mimoUnavailable";
    public static final String AI_PROMPT_NOT_FOUND = "errors.aiPromptNotFound";
    public static final String AI_CONTINUE_PROMPT_NOT_FOUND = "errors.aiContinuePromptNotFound";
    public static final String AI_HEADER_ECHO = "errors.aiHeaderEcho";
    public static final String AI_INVALID_JSON = "errors.aiInvalidJson";
    public static final String AI_NO_PARSEABLE_RECORDS = "errors.aiNoParseableRecords";
    public static final String AI_FABRICATED = "errors.aiFabricated";
    public static final String AI_MALFORMED_RECORDS = "errors.aiMalformedRecords";
    public static final String AI_UNREADABLE_TIMES = "errors.aiUnreadableTimes";
    public static final String AI_IMAGE_TOO_BLURRY = "errors.aiImageTooBlurry";
    public static final String UPLOAD_IMAGE_TOO_BLURRY = "errors.uploadImageTooBlurry";

    public static final String VALIDATION_FAILED = "errors.validationFailed";
    public static final String MISSING_PARAMETER = "errors.missingParameter";
    public static final String FILE_SIZE_EXCEEDED = "errors.fileSizeExceeded";
    public static final String API_NOT_FOUND = "errors.apiNotFound";
    public static final String METHOD_NOT_ALLOWED = "errors.methodNotAllowed";
    public static final String DB_MIGRATION_REQUIRED = "errors.dbMigrationRequired";
    public static final String SYSTEM_ERROR = "errors.systemError";
    public static final String REQUEST_FAILED = "errors.requestFailed";
    public static final String NETWORK_ERROR = "errors.networkError";

    private ErrorKeys() {
    }
}
