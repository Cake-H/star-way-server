package com.smartapply.ida.plugin.boot;

import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.http.RequestIdentityConstants;
import com.smartapply.ida.module.plugin.auth.service.LoginSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 插件进程专用 Bearer 解析：绑定 {@code pluginLoginSessionService}，与正式端 {@code appLoginSessionService} 解耦；
 * Redis 键前缀若配置相同，两端签发的 Token 仍可互认（按需统一或拆分库索引）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
public class PluginBearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final HttpIdentityProperties httpIdentityProperties;
    private final AuthSessionProperties authSessionProperties;
    private final LoginSessionService loginSessionService;

    public PluginBearerTokenAuthenticationFilter(
            HttpIdentityProperties httpIdentityProperties,
            AuthSessionProperties authSessionProperties,
            @Qualifier("pluginLoginSessionService") LoginSessionService loginSessionService) {
        this.httpIdentityProperties = httpIdentityProperties;
        this.authSessionProperties = authSessionProperties;
        this.loginSessionService = loginSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerName = httpIdentityProperties.getAuthorizationHeader();
        if (!StringUtils.hasText(headerName)) {
            filterChain.doFilter(request, response);
            return;
        }
        String raw = request.getHeader(headerName);
        if (!StringUtils.hasText(raw)) {
            filterChain.doFilter(request, response);
            return;
        }
        String prefix = authSessionProperties.getBearerPrefix();
        if (!StringUtils.hasText(prefix) || !raw.startsWith(prefix)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = raw.substring(prefix.length()).trim();
        loginSessionService.findUserIdByToken(token)
                .ifPresent(uid -> request.setAttribute(RequestIdentityConstants.ATTR_USER_ID, uid));
        filterChain.doFilter(request, response);
    }
}
