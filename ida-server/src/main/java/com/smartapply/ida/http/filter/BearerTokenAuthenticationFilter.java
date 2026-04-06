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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer Token 鉴权过滤器（顺序 {@link Ordered#HIGHEST_PRECEDENCE} + 8）。
 * <p>
 * 从 {@link com.smartapply.ida.config.props.http.HttpIdentityProperties#getAuthorizationHeader()} 指定头读取原始值，
 * 若以前缀 {@link com.smartapply.ida.config.props.auth.AuthSessionProperties#getBearerPrefix()} 开头，则截取 Token 段，
 * 经 {@link com.smartapply.ida.module.app.auth.service.LoginSessionService#findUserIdByToken(String)} 解析用户 id，
 * 写入 {@link com.smartapply.ida.http.RequestIdentityConstants#ATTR_USER_ID}。
 * <p>
 * 与 {@link UserIdHeaderCaptureFilter} 配合：本过滤器先执行；若已解析出用户 id，后者不再用 {@code X-User-Id} 覆盖。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
@RequiredArgsConstructor
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final HttpIdentityProperties httpIdentityProperties;
    private final AuthSessionProperties authSessionProperties;
    private final LoginSessionService loginSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 未配置头名：整条链跳过本过滤器（等价于未启用 Bearer 解析）
        String headerName = httpIdentityProperties.getAuthorizationHeader();
        if (!StringUtils.hasText(headerName)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 无 Authorization（或其它配置名）头：匿名继续，后续可由 X-User-Id 等兜底
        String raw = request.getHeader(headerName);
        if (!StringUtils.hasText(raw)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 非 Bearer 格式（如 Basic）不处理，避免误把整段当 token 查 Redis
        String prefix = authSessionProperties.getBearerPrefix();
        if (!StringUtils.hasText(prefix) || !raw.startsWith(prefix)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 仅截取前缀之后部分作为 Token，不做 JWT 解析（当前为服务端随机串 + Redis）
        String token = raw.substring(prefix.length()).trim();
        // Token 无效/过期时不写属性，与「未登录」一致；不写 401，由具体接口 @RequestUserId(required=true) 等决定
        loginSessionService.findUserIdByToken(token)
                .ifPresent(uid -> request.setAttribute(RequestIdentityConstants.ATTR_USER_ID, uid));
        filterChain.doFilter(request, response);
    }
}
