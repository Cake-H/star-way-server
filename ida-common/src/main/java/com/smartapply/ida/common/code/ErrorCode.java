package com.smartapply.ida.common.code;

/**
 * 错误码约定（对齐原 SmartAdmin 模板分层：系统 / 用户 / 未预期）。
 * <p>
 * 智能投递助手后续可在本接口上扩展业务错误枚举。
 */
public interface ErrorCode {

    String LEVEL_SYSTEM = "system";
    String LEVEL_USER = "user";
    String LEVEL_UNEXPECTED = "unexpected";

    int getCode();

    String getMsg();

    String getLevel();
}
