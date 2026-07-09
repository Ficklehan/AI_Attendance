package com.attendance.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常: code={}, messageKey={}", e.getCode(), e.getMessageKey());
        return Result.error(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("参数校验异常: {}", detail);
        return Result.error(400, ErrorKeys.VALIDATION_FAILED, Collections.singletonMap("detail", detail));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("参数绑定异常: {}", detail);
        return Result.error(400, ErrorKeys.VALIDATION_FAILED, Collections.singletonMap("detail", detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.error("约束违规异常: {}", detail);
        return Result.error(400, ErrorKeys.VALIDATION_FAILED, Collections.singletonMap("detail", detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.error("缺少请求参数: {}", e.getMessage());
        return Result.error(400, ErrorKeys.MISSING_PARAMETER, Collections.singletonMap("name", e.getParameterName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件大小超出限制: {}", e.getMessage());
        return Result.error(400, ErrorKeys.FILE_SIZE_EXCEEDED);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.error("接口不存在: {}", e.getRequestURL());
        return Result.error(404, ErrorKeys.API_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("请求方法不支持: {}", e.getMessage());
        return Result.error(405, ErrorKeys.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(SQLException.class)
    public Result<Void> handleSQLException(SQLException e) {
        log.error("数据库异常: {}", e.getMessage(), e);
        return resolveDbError(e);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        SQLException sql = findSQLException(e);
        if (sql != null) {
            log.error("数据库异常: {}", sql.getMessage(), e);
            return resolveDbError(sql);
        }
        if (isNetworkException(e)) {
            log.error("网络异常: {}", e.getMessage(), e);
            return Result.error(503, ErrorKeys.NETWORK_ERROR);
        }
        log.error("系统异常", e);
        return Result.error(500, ErrorKeys.SYSTEM_ERROR);
    }

    private boolean isNetworkException(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof UnknownHostException
                    || cur instanceof ConnectException
                    || cur instanceof SocketTimeoutException) {
                return true;
            }
            if (cur instanceof IOException && cur.getMessage() != null
                    && cur.getMessage().contains("open.feishu.cn")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private Result<Void> resolveDbError(SQLException e) {
        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("export_jobs") && msg.contains("doesn't exist")) {
                return Result.error(500, ErrorKeys.DB_MIGRATION_REQUIRED);
            }
            // 角色数据权限：存量库未扩 ENUM 时写入 work_region 会 Data truncated
            if (msg.contains("role_data_dimension_rule")
                    || (msg.contains("Data truncated") && msg.contains("dimension"))
                    || msg.contains("work_region")) {
                return Result.error(500, ErrorKeys.DB_MIGRATION_REQUIRED);
            }
            if (msg.contains("Unknown column") || msg.contains("doesn't exist")) {
                return Result.error(500, ErrorKeys.DB_MIGRATION_REQUIRED);
            }
        }
        return Result.error(500, ErrorKeys.SYSTEM_ERROR);
    }

    private SQLException findSQLException(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SQLException) {
                return (SQLException) cur;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
