package com.smartapply.ida.config.oss;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * {@code ida.aliyun.oss.enabled=true} 且 endpoint、AK、SK、bucket 均已配置时，才注册 {@link com.aliyun.oss.OSS} Bean，
 * 避免占位环境变量为空时 {@link com.aliyun.oss.OSSClientBuilder#build} 直接抛错导致应用无法启动。
 */
public class AliyunOssClientAvailableCondition implements Condition {

    private static final String PREFIX = "ida.aliyun.oss.";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String enabled = env.getProperty(PREFIX + "enabled");
        if (!"true".equalsIgnoreCase(enabled != null ? enabled.trim() : "")) {
            return false;
        }
        return StringUtils.hasText(env.getProperty(PREFIX + "endpoint"))
                && StringUtils.hasText(env.getProperty(PREFIX + "access-key-id"))
                && StringUtils.hasText(env.getProperty(PREFIX + "access-key-secret"))
                && StringUtils.hasText(env.getProperty(PREFIX + "bucket"));
    }
}
