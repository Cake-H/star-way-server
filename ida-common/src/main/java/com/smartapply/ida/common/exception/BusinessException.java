package com.smartapply.ida.common.exception;

import com.smartapply.ida.common.code.ErrorCode;

/**
 * 业务异常：在 Service 层主动抛出，由 {@link com.smartapply.ida.handler.GlobalExceptionHandler}
 * 捕获并转为 {@link com.smartapply.ida.common.domain.ResponseDTO}，不向 HTTP 层继续冒泡未处理异常。
 */
public class BusinessException extends RuntimeException {

    /** 可选：指定后全局异常处理器按此码返回，缺省回退 {@code UserErrorCode.OPERATION_FAILED} */
    private ErrorCode errorCode;

    public BusinessException() {
    }

    /** 使用错误码默认文案作为 message */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    /** 自定义对用户可见的说明（与 UserErrorCode 等配合） */
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 指定错误码 + 自定义文案（如额度不足需前端引导充值） */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
