package com.smartapply.ida.common.exception;

import com.smartapply.ida.common.code.ErrorCode;

/**
 * 业务异常：在 Service 层主动抛出，由 {@link com.smartapply.ida.handler.GlobalExceptionHandler}
 * 捕获并转为 {@link com.smartapply.ida.common.domain.ResponseDTO}，不向 HTTP 层继续冒泡未处理异常。
 */
public class BusinessException extends RuntimeException {

    public BusinessException() {
    }

    /** 使用错误码默认文案作为 message */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
    }

    /** 自定义对用户可见的说明（与 UserErrorCode 等配合） */
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
