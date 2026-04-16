package com.smartapply.ida.http;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * 当前请求上下文中的「已解析用户身份」读取器。
 * <p>
 * 用户 id 可能来自 {@link com.smartapply.ida.http.filter.BearerTokenAuthenticationFilter}（Bearer Token）
 * 或 {@link com.smartapply.ida.http.filter.UserIdHeaderCaptureFilter}（如 {@code X-User-Id}），二者写入同一请求属性键。
 * <p>
 * 注意：非 Web 线程（异步任务、定时任务）调用时 {@link RequestContextHolder} 无绑定，返回 {@link Optional#empty()}。
 */
@Component
public class CurrentUserIdentity {

    /** 当前登录用户主键；未鉴权或匿名请求时为 empty */
    public Optional<Long> userId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        Object v = attrs.getAttribute(RequestIdentityConstants.ATTR_USER_ID, RequestAttributes.SCOPE_REQUEST);
        return v instanceof Long l ? Optional.of(l) : Optional.empty();
    }

    /** 来自 {@code X-Request-Id} 等头，用于链路追踪（若过滤器未写入则为 empty） */
    public Optional<String> requestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        Object v = attrs.getAttribute(RequestIdentityConstants.ATTR_REQUEST_ID, RequestAttributes.SCOPE_REQUEST);
        return v instanceof String s ? Optional.of(s) : Optional.empty();
    }
}
