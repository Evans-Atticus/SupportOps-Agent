package com.example.supportops.module.ai;

import com.example.supportops.common.exception.ErrorCode;

/** 异步流程内部使用的模型异常，保留已归一化错误码用于降级和审计。 */
public class AiCallException extends RuntimeException {
    private final ErrorCode errorCode;

    public AiCallException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
