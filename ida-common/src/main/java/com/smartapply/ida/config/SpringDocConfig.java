package com.smartapply.ida.config;

import com.smartapply.ida.http.annotation.NoNeedLogin;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * SpringDoc / Knife4j 全局配置。
 * <p>
 * 1. 启用全局 Bearer Token 鉴权，Swagger / Knife4j 文档显示 Authorize 按钮。
 * 2. 对 {@link NoNeedLogin} 标注的接口（登录、发验证码、健康检查）移除安全要求，不显示锁图标。
 * <p>
 * 说明：Knife4j 4.x 的解析规则是“只有 Operation 级别存在 security 属性时才认为需要鉴权”，
 * 因此除了全局安全方案外，还需要通过 {@link GlobalOpenApiCustomizer} 给每个 Operation 显式添加 security。
 */
@Configuration
public class SpringDocConfig {

    private static final String BEARER_SCHEME = "Bearer";
    private static final String NO_NEED_LOGIN_EXTENSION = "x-no-need-login";

    /**
     * 通过 OpenApiCustomizer 在已有 OpenAPI 对象上追加 Bearer Token 安全方案。
     * 使用 customizer 而不是直接定义 OpenAPI Bean，避免与 springdoc/knife4j 自动配置冲突。
     */
    @Bean
    public OpenApiCustomizer bearerTokenOpenApiCustomizer() {
        return openApi -> {
            openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
            if (openApi.getComponents() == null) {
                openApi.setComponents(new io.swagger.v3.oas.models.Components());
            }
            openApi.getComponents().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));
        };
    }

    /**
     * 自定义 Operation：带有 {@link NoNeedLogin} 的接口不需要 Bearer Token。
     * 这里通过 vendor extension 标记，后续 {@link #operationSecurityGlobalOpenApiCustomizer()} 会跳过这些接口。
     */
    @Bean
    public OperationCustomizer noNeedLoginOperationCustomizer() {
        return (operation, handlerMethod) -> {
            boolean noNeedLogin = handlerMethod.hasMethodAnnotation(NoNeedLogin.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(NoNeedLogin.class);
            if (noNeedLogin) {
                operation.setSecurity(Collections.emptyList());
                operation.addExtension(NO_NEED_LOGIN_EXTENSION, true);
            }
            return operation;
        };
    }

    /**
     * 为所有非 {@link NoNeedLogin} 的 Operation 显式添加 Bearer security。
     * 这是 Knife4j 4.x 能正确在调试请求中携带 Authorization 的关键。
     */
    @Bean
    public GlobalOpenApiCustomizer operationSecurityGlobalOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (pathItem == null) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> {
                    Object noNeedLogin = operation.getExtensions() == null
                            ? null : operation.getExtensions().get(NO_NEED_LOGIN_EXTENSION);
                    if (!Boolean.TRUE.equals(noNeedLogin)) {
                        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                    }
                });
            });
        };
    }
}
