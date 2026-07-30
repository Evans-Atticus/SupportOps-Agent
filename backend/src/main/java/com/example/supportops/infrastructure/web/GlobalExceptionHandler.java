package com.example.supportops.infrastructure.web;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), exception.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), errors, requestId(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), exception.getMessage(), null, requestId(request)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), null, requestId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), "请求体必须是有效的 JSON", null, requestId(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), "参数格式不正确: " + exception.getName(),
                        null, requestId(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), "缺少必填参数: " + exception.getParameterName(),
                        null, requestId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), null, requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled request failure, requestId={}", requestId, exception);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), null, requestId));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException exception, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Database failure, requestId={}", requestId, exception);
        ErrorCode code = ErrorCode.DATABASE_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), null, requestId));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
