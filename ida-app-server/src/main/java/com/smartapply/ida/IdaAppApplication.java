package com.smartapply.ida;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;

/**
 * 正式客户端 — 独立 Spring Boot 进程（默认端口 {@code 8088}）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = {
        "com.smartapply.ida.config.props",
        "com.smartapply.ida.ai.speech.config"
})
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
        ConfigurableApplicationContext ctx = SpringApplication.run(IdaAppApplication.class, args);
        printAccessInfo(ctx);
    }

    private static void printAccessInfo(ConfigurableApplicationContext ctx) {
        if (!(ctx instanceof WebServerApplicationContext webCtx)) {
            return;
        }
        Environment env = ctx.getEnvironment();
        String host = env.getProperty("ida.server.access-host", "127.0.0.1").trim();
        int port = webCtx.getWebServer().getPort();
        String api = env.getProperty("ida.server.api-v1-prefix", "/api/v1/app");
        String base = "http://" + host + ":" + port;

        System.out.println();
        System.out.println(">>> " + env.getProperty("spring.application.name") + " 启动完成");
        System.out.println(">>> API  " + base + api);
        System.out.println(">>> 文档 " + base + "/doc.html");
        System.out.println();
    }
}
