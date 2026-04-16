package com.smartapply.ida.http.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.http.RequestIdentityConstants;
import com.smartapply.ida.http.annotation.NoNeedLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 默认要求 {@code /api/**} 已登录；{@link NoNeedLogin} 用于登录、探活等匿名接口。
 * <p>
 * 须在 Bearer / 用户头过滤器之后执行，依赖 {@link RequestIdentityConstants#ATTR_USER_ID} 是否已写入。
 */
@Component
@RequiredArgsConstructor
public class LoginRequiredInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                               @NonNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        if (isNoNeedLogin(hm)) {
            return true;
        }
        Object uid = request.getAttribute(RequestIdentityConstants.ATTR_USER_ID);
        if (uid instanceof Long) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
        return false;
    }

    private static boolean isNoNeedLogin(HandlerMethod hm) {
        return hm.hasMethodAnnotation(NoNeedLogin.class)
                || hm.getBeanType().isAnnotationPresent(NoNeedLogin.class);
    }
}
