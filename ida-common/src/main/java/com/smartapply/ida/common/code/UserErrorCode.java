package com.smartapply.ida.common.code;

import lombok.Getter;

/**
 * 用户侧可预期错误（参数、登录态、权限等）。
 * 可按业务继续追加枚举常量，注意 code 不要与 {@link SystemErrorCode} 冲突。
 */
@Getter
public enum UserErrorCode implements ErrorCode {

    PARAM_ERROR(30001, "参数错误"),
    LOGIN_STATE_INVALID(30007, "未登录或登录已失效，请重新登录"),
    NO_PERMISSION(30005, "无权限执行此操作"),
    /** 业务规则不满足时抛出 {@link com.smartapply.ida.common.exception.BusinessException} 后映射到此码 */
    OPERATION_FAILED(30100, "操作无法完成");

    private final int code;
    private final String msg;
    private final String level;

    UserErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.level = LEVEL_USER;
    }
}
