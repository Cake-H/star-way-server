package com.smartapply.ida.common.code;

import lombok.Getter;

/**
 * 系统级错误（未捕获异常、基础设施故障等），应记录日志并告警。
 */
@Getter
public enum SystemErrorCode implements ErrorCode {

    SYSTEM_ERROR(10001, "系统繁忙，请稍后再试");

    private final int code;
    private final String msg;
    private final String level;

    SystemErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.level = LEVEL_SYSTEM;
    }
}
