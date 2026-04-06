package com.smartapply.ida.common.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 与 spring.profiles.active 对齐，用于全局异常中是否向客户端透出详细错误。
 */
@Getter
@AllArgsConstructor
public enum SystemEnvironmentEnum {

    DEV("dev", "开发环境"),
    TEST("test", "测试环境"),
    PRE("pre", "预发布环境"),
    PROD("prod", "生产环境");

    private final String value;
    private final String desc;
}
