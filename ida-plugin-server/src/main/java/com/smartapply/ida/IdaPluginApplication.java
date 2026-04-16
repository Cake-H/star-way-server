package com.smartapply.ida;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import com.smartapply.ida.module.app.resume.service.impl.AppResumeUploadParseSeviceImpl;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 浏览器扩展 — 独立 Spring Boot 进程（默认端口 {@code 8089}）。
 * <p>
 * 仍依赖 {@code ida-app} JAR 中的简历读、用户 Mapper、短信/OSS 集成等 Bean，但通过组件扫描排除
 * 正式端 {@code RestController}，避免同一端口暴露两套 API。与 {@link IdaAppApplication} 分进程后，
 * 一端 OOM 或崩溃不会拖垮另一端 JVM。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.smartapply.ida.config.props")
@MapperScan(value = "com.smartapply.ida", annotationClass = Mapper.class)
@ComponentScan(
        basePackages = "com.smartapply.ida",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.app\\.auth\\.controller\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.app\\.user\\.controller\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.app\\.health\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.app\\.resume\\.controller\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.ai\\.resume\\..*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppResumeUploadParseSeviceImpl.class)
        }
)
public class IdaPluginApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdaPluginApplication.class, args);
    }
}
