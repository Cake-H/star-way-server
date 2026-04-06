package com.smartapply.ida.config;

import com.smartapply.ida.config.props.CorsProperties;
import com.smartapply.ida.http.interceptor.LoginRequiredInterceptor;
import com.smartapply.ida.http.resolver.RequestUserIdMethodArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Web MVC 横切配置。
 * <ul>
 *   <li>CORS：仅对 {@code /api/**} 生效，来源与暴露头来自 {@link CorsProperties}</li>
 *   <li>参数解析：注册 {@link RequestUserIdMethodArgumentResolver}，支持 {@code @RequestUserId Long}</li>
 *   <li>登录：{@code /api/**} 默认需已解析用户 id；{@link com.smartapply.ida.http.annotation.NoNeedLogin} 放行匿名接口</li>
 *   <li>JSON：为 Jackson 增加 {@code text/plain}，兼容误设 Content-Type 的客户端（body 仍为 JSON）</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;
    private final LoginRequiredInterceptor loginRequiredInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = corsProperties.getAllowedOriginPatterns().toArray(String[]::new);
        var registration = registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
        List<String> exposed = corsProperties.getExposedHeaders();
        if (!exposed.isEmpty()) {
            registration.exposedHeaders(exposed.toArray(String[]::new));
        }
    }

    /**
     * 注册无状态的 {@link RequestUserIdMethodArgumentResolver}（每次 new 即可）。
     */
    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestUserIdMethodArgumentResolver());
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(loginRequiredInterceptor).addPathPatterns("/api/**");
    }

    /**
     * 部分工具以 {@code Content-Type: text/plain} 发送 JSON 体，默认 Jackson 只接受 {@code application/json}，会 415。
     */
    @Override
    public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                List<MediaType> types = new ArrayList<>(jackson.getSupportedMediaTypes());
                if (types.stream().noneMatch(t -> MediaType.TEXT_PLAIN.equalsTypeAndSubtype(t))) {
                    types.add(MediaType.TEXT_PLAIN);
                }
                jackson.setSupportedMediaTypes(types);
                return;
            }
        }
    }
}
