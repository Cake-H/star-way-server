package com.smartapply.ida.http.filter;

import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.http.RequestIdentityConstants;
import com.smartapply.ida.module.app.auth.service.LoginSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer Token 鉴权过滤器。
 * <p>
 * 从 {@code Authorization: Bearer <token>} 头中解析令牌，调用 {@link LoginSessionService}
 * 查询 Redis 中的 userId，写入请求属性供 {@link com.smartapply.ida.http.resolver.RequestUserIdMethodArgumentResolver}
 * 与 {@link com.smartapply.ida.http.interceptor.LoginRequiredInterceptor} 使用。
 * <p>
 * 执行顺序早于 {@link UserIdHeaderCaptureFilter}，避免调试头 {@code X-User-Id} 覆盖真实登录态。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    @Qualifier("appLoginSessionService")
    private final LoginSessionService loginSessionService;
    private final HttpIdentityProperties identityProperties;
    private final AuthSessionProperties authSessionProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        captureBearerToken(request);
        filterChain.doFilter(request, response);
    }

    private void captureBearerToken(HttpServletRequest request) {
        // 若请求属性中已有 userId（如被更前置的过滤器设置），不再解析，防止覆盖
        if (request.getAttribute(RequestIdentityConstants.ATTR_USER_ID) != null) {
            log.debug("请求属性中已存在 userId，跳过 Bearer 解析");
            return;
        }

        String authHeader = request.getHeader(identityProperties.getAuthorizationHeader());
        String prefix = authSessionProperties.getBearerPrefix();
        if (!StringUtils.hasText(authHeader) || !StringUtils.hasText(prefix)
                || !authHeader.startsWith(prefix)) {
            log.debug("Authorization 头不存在或不符合 Bearer 前缀格式");
            return;
        }

        String token = authHeader.substring(prefix.length()).trim();
        if (!StringUtils.hasText(token)) {
            log.warn("Authorization 头中 token 为空");
            return;
        }

        loginSessionService.findUserIdByToken(token).ifPresentOrElse(userId -> {
            request.setAttribute(RequestIdentityConstants.ATTR_USER_ID, userId);
            log.debug("Bearer Token 解析成功，userId={}", userId);
        }, () -> log.warn("Bearer Token 在 Redis 中未找到或已过期，token={}", token));
    }
}
