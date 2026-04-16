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
 * Bearer Token 鉴权：解析 Token 后写入请求属性；绑定正式端 {@link LoginSessionService}（Bean 名 {@code appLoginSessionService}）。
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
