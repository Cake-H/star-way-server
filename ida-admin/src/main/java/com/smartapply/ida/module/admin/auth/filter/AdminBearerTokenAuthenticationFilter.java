package com.smartapply.ida.module.admin.auth.filter;

import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.http.RequestIdentityConstants;
import com.smartapply.ida.module.admin.auth.service.AdminSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理后台 Bearer Token 鉴权过滤器（对齐 C 端 {@code BearerTokenAuthenticationFilter} 结构）。
 * <p>
 * 从 {@code Authorization: Bearer <token>} 解析管理员会话，将管理员主键写入
 * {@link RequestIdentityConstants#ATTR_USER_ID} 请求属性（复用统一的身份属性键，
 * 使 {@code @RequestUserId} 参数解析与 {@code LoginRequiredInterceptor} 无需改动即可复用）。
 * <p>
 * <b>安全说明：</b>本进程不装配 C 端的 {@code UserIdHeaderCaptureFilter}，
 * {@code X-User-Id} 调试头在后台不可伪造身份。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AdminBearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AdminSessionService adminSessionService;
    private final HttpIdentityProperties identityProperties;
    private final AuthSessionProperties authSessionProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        captureBearerToken(request);
        filterChain.doFilter(request, response);
    }

    private void captureBearerToken(HttpServletRequest request) {
        // 若请求属性中已有身份（被更前置的过滤器设置），不再解析，防止覆盖
        if (request.getAttribute(RequestIdentityConstants.ATTR_USER_ID) != null) {
            return;
        }

        String authHeader = request.getHeader(identityProperties.getAuthorizationHeader());
        String prefix = authSessionProperties.getBearerPrefix();
        if (!StringUtils.hasText(authHeader) || !StringUtils.hasText(prefix)
                || !authHeader.startsWith(prefix)) {
            return;
        }

        String token = authHeader.substring(prefix.length()).trim();
        if (!StringUtils.hasText(token)) {
            return;
        }

        // Token 值不打印日志，避免会话凭证泄露
        adminSessionService.findAdminIdByToken(token).ifPresentOrElse(adminId -> {
            request.setAttribute(RequestIdentityConstants.ATTR_USER_ID, adminId);
            log.debug("管理员 Bearer Token 解析成功，adminId={}", adminId);
        }, () -> log.debug("管理员 Bearer Token 未找到或已过期"));
    }
}
