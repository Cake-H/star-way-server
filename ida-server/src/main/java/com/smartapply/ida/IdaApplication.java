package com.smartapply.ida;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 智能投递助手 — 后端启动类。
 * <p>
 * {@link MapperScan} 必须带 {@code annotationClass = Mapper.class}，否则整包扫描会把
 * {@link com.smartapply.ida.integration.sms.SmsTemplateSender} 等普通接口当成 Mapper，与 {@code smsTemplateSender} Bean 重名冲突。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.smartapply.ida.config.props")
@MapperScan(value = "com.smartapply.ida", annotationClass = Mapper.class)
public class IdaApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdaApplication.class, args);
    }
}
