package com.smartapply.ida.http;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 存放在 {@link jakarta.servlet.http.HttpServletRequest#setAttribute(String, Object)} 中的键名常量，
 * 供过滤器、{@link org.springframework.web.method.support.HandlerMethodArgumentResolver}、{@link CurrentUserIdentity} 共用。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestIdentityConstants {

    /** 类型 {@link Long}；未登录或未解析时请求上不设置该属性 */
    public static final String ATTR_USER_ID = "ida.request.userId";

    /** 类型 {@link String}，可选 */
    public static final String ATTR_REQUEST_ID = "ida.request.requestId";
}
