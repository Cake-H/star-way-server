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
        try {
            String enabled = env.getProperty(PREFIX + "enabled");
            System.out.println("[OSS-Condition] enabled=" + enabled);
            if (!"true".equalsIgnoreCase(enabled != null ? enabled.trim() : "")) {
                System.out.println("[OSS-Condition] enabled is not true → skip");
                return false;
            }
            String ep = env.getProperty(PREFIX + "endpoint");
            String ak = env.getProperty(PREFIX + "access-key-id");
            String sk = env.getProperty(PREFIX + "access-key-secret");
            String bk = env.getProperty(PREFIX + "bucket");
            System.out.println("[OSS-Condition] endpoint=" + ep + " ak=" + (ak != null ? "***" : "null") + " sk=" + (sk != null ? "***" : "null") + " bucket=" + bk);
            boolean ok = org.springframework.util.StringUtils.hasText(ep)
                    && org.springframework.util.StringUtils.hasText(ak)
                    && org.springframework.util.StringUtils.hasText(sk)
                    && org.springframework.util.StringUtils.hasText(bk);
            System.out.println("[OSS-Condition] result=" + ok);
            return ok;
        } catch (Exception e) {
            System.out.println("[OSS-Condition] EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }
}
