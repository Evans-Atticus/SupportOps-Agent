package com.example.supportops.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    MISSING_BUSINESS_NO(HttpStatus.BAD_REQUEST, "缺少业务编号"),
    UNKNOWN_SCENARIO(HttpStatus.BAD_REQUEST, "无法识别诊断场景"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或凭证无效"),
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "账号已存在"),
    LOGIN_IP_ACCOUNT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "同一网络一小时内登录的账号数量已达上限，请稍后再试或使用已登录过的账号"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行此操作"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    RESOURCE_IN_USE(HttpStatus.CONFLICT, "资源已被业务数据引用，无法删除"),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "工单不存在"),
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "诊断任务不存在"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "诊断状态不能执行此操作"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "请求已处理"),
    DAILY_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "今日演示额度已用完"),
    AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "模型服务限流"),
    AI_QUOTA_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "模型额度已用完"),
    AI_USAGE_RATE_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "调用过于频繁，已为保护模型额度拦截，请稍后重试"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "模型服务不可用"),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "模型服务超时"),
    AI_RESPONSE_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "模型响应解析失败"),
    INTEGRATION_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "外部平台尚未配置"),
    INTEGRATION_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "外部平台暂时不可用"),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "数据库操作失败"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
