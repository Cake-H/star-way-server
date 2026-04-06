package com.smartapply.ida.http.resolver;

import com.smartapply.ida.http.RequestIdentityConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * 将 {@link RequestUserId} 标注的 {@link Long} 形参与请求属性 {@link com.smartapply.ida.http.RequestIdentityConstants#ATTR_USER_ID} 绑定。
 */
public class RequestUserIdMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    /**
     * 未登录且 {@link RequestUserId#required()} 为 false 时返回 null；required 为 true 且无用户 id 时返回 401。
     */
    @Override
    @Nullable
    public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object v = request != null ? request.getAttribute(RequestIdentityConstants.ATTR_USER_ID) : null;
        RequestUserId ann = parameter.getParameterAnnotation(RequestUserId.class);
        if (v instanceof Long l) {
            return l;
        }
        if (ann != null && ann.required()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少或无效的用户身份请求头");
        }
        return null;
    }
}
