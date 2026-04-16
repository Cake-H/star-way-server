package com.smartapply.ida.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在 Controller 类或方法上：该接口允许匿名访问，不校验登录态。
 * <p>
 * 未标注时，{@link com.smartapply.ida.http.interceptor.LoginRequiredInterceptor} 对 {@code /api/**}
 * 要求请求上已解析出用户 id（Bearer Token 或可信 {@code X-User-Id} 等）；否则返回 401 与统一 {@link com.smartapply.ida.common.domain.ResponseDTO}。
 * <p>
 * 方法上的注解优先于类上的注解（二者任一存在即视为放行）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoNeedLogin {
}
