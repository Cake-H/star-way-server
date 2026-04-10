package com.smartapply.ida.config.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.smartapply.ida.config.props.oss.AliyunOssProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 条件装配 OSS 客户端：见 {@link AliyunOssClientAvailableCondition}。
 */
@Configuration
public class AliyunOssConfiguration {

    @Bean(destroyMethod = "shutdown")
    @Conditional(AliyunOssClientAvailableCondition.class)
    public OSS aliyunOssClient(AliyunOssProperties p) {
        return new OSSClientBuilder().build(p.getEndpoint(), p.getAccessKeyId(), p.getAccessKeySecret());
    }
}
