package com.smartapply.ida.http.filter;

import com.smartapply.ida.config.props.http.HttpIdentityProperties;
import com.smartapply.ida.http.RequestIdentityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 网关/调试场景：通过显式请求头传入用户主键（如 {@code X-User-Id}）。
 * <p>
 * 执行顺序晚于 {@link BearerTokenAuthenticationFilter}：若请求已由 Bearer 解析出用户 id，本过滤器直接跳过，避免头信息覆盖登录态。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class UserIdHeaderCaptureFilter extends OncePerRequestFilter {

    private final HttpIdentityProperties identityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        captureUserId(request);
        captureRequestId(request);
        filterChain.doFilter(request, response);
    }

    private void captureUserId(HttpServletRequest request) {
        // 安全开关：默认关闭，防止伪造 X-User-Id 冒充任意用户；仅本地联调显式开启
        if (!identityProperties.isUserIdHeaderEnabled()) {
            return;
        }
        // Bearer 已成功解析时不再读头，防止网关误传 X-User-Id 覆盖真实登录用户
        if (request.getAttribute(RequestIdentityConstants.ATTR_USER_ID) != null) {
            return;
        }
        String name = identityProperties.getUserIdHeader();
        if (name == null || name.isBlank()) {
            return;
        }
        String raw = request.getHeader(name);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            // 信任上游：此处不做签名校验，仅解析数字；生产应在网关统一鉴权后再注入该头
            request.setAttribute(RequestIdentityConstants.ATTR_USER_ID, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            log.debug("请求头 {} 值非合法 Long，已忽略: {}", name, raw);
        }
    }

    private void captureRequestId(HttpServletRequest request) {
        String name = identityProperties.getRequestIdHeader();
        if (name == null || name.isBlank()) {
            return;
        }
        String rid = request.getHeader(name);
        if (rid != null && !rid.isBlank()) {
            request.setAttribute(RequestIdentityConstants.ATTR_REQUEST_ID, rid.trim());
        }
    }
}
