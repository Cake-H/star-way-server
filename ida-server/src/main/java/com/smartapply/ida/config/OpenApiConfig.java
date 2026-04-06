package com.smartapply.ida.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档：按「插件端 / 正式端」分组，便于两套客户端分别对接。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI idaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能投递助手 API")
                        .description("plugin：浏览器扩展；app：桌面/正式客户端")
                        .version("v1"));
    }

    @Bean
    public GroupedOpenApi pluginGroupedApi() {
        return GroupedOpenApi.builder()
                .group("plugin")
                .displayName("插件端")
                .pathsToMatch("/api/v1/plugin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi appGroupedApi() {
        return GroupedOpenApi.builder()
                .group("app")
                .displayName("正式客户端")
                .pathsToMatch("/api/v1/app/**")
                .build();
    }
}
