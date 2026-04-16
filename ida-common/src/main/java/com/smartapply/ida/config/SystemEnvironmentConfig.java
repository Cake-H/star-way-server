package com.smartapply.ida.config;

import com.smartapply.ida.common.domain.SystemEnvironment;
import com.smartapply.ida.common.enumeration.SystemEnvironmentEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配 {@link SystemEnvironment}，供 ida-common 中全局异常根据是否生产环境决定日志与返回细节。
 */
@Configuration
public class SystemEnvironmentConfig {

    @Bean
    public SystemEnvironment systemEnvironment(
            @Value("${spring.application.name:ida-app-server}") String applicationName,
            @Value("${spring.profiles.active:dev}") String rawProfiles) {
        // 支持多 profile：dev,local → 取第一个判断环境
        String primary = rawProfiles.split(",")[0].trim();
        SystemEnvironmentEnum env = switch (primary) {
            case "prod" -> SystemEnvironmentEnum.PROD;
            case "pre" -> SystemEnvironmentEnum.PRE;
            case "test" -> SystemEnvironmentEnum.TEST;
            default -> SystemEnvironmentEnum.DEV;
        };
        boolean prod = env == SystemEnvironmentEnum.PROD;
        return new SystemEnvironment(prod, applicationName, env);
    }
}
