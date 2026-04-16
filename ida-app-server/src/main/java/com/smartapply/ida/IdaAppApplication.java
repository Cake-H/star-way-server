package com.smartapply.ida;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 正式客户端 — 独立 Spring Boot 进程（默认端口 {@code 8088}）。
 * <p>
 * 与 {@link com.smartapply.ida.IdaPluginApplication} 分离：不扫描插件包，避免插件 Controller/Service 与本进程混用；
 * 插件端崩溃或发布不影响本进程。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.smartapply.ida.config.props")
@MapperScan(value = "com.smartapply.ida", annotationClass = Mapper.class)
@ComponentScan(
        basePackages = "com.smartapply.ida",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.plugin\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.ai\\.plugin\\..*")
        }
)
public class IdaAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdaAppApplication.class, args);
    }
}
