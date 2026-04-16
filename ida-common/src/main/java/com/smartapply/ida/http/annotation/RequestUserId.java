package com.smartapply.ida.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 方法参数注解：注入 {@link com.smartapply.ida.http.RequestIdentityConstants#ATTR_USER_ID}。
 * <p>
 * 解析链：Bearer Token 过滤器与（可选）用户头过滤器写入属性后，由 {@link com.smartapply.ida.http.resolver.RequestUserIdMethodArgumentResolver} 绑定到 {@link Long} 参数。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestUserId {

    /**
     * 为 true 时，若请求上不存在合法用户 id，则返回 HTTP 401（适用于必须登录的接口）。
     */
    boolean required() default false;
}
