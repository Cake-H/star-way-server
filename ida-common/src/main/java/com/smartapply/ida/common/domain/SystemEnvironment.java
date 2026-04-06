package com.smartapply.ida.common.domain;

import com.smartapply.ida.common.enumeration.SystemEnvironmentEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前进程运行环境快照，由 ida-server 在启动时根据配置装配，供 {@link com.smartapply.ida.handler.GlobalExceptionHandler} 使用。
 */
@Getter
@AllArgsConstructor
public class SystemEnvironment {

    /** 是否为生产环境（生产环境对用户隐藏异常堆栈细节） */
    private final boolean prod;

    /** 应用名，便于日志串联 */
    private final String applicationName;

    private final SystemEnvironmentEnum currentEnvironment;
}
