package com.smartapply.ida.ai.plugin.site;

import org.springframework.util.StringUtils;

/**
 * 页面 URL 解析工具：从 pageUrl 提取 host，供站点指纹匹配与反馈按站点聚合复用。
 */
public final class PageUrlUtils {

    private PageUrlUtils() {}

    /**
     * 从 url 提取小写 host（去端口、去 userinfo），无法解析返回空串。
     * <p>
     * 简易解析，避免 {@code java.net.URI} 对非标 URL 抛异常。
     */
    public static String extractHost(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String u = url.trim();
        int scheme = u.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int slash = u.indexOf('/', start);
        String authority = slash > 0 ? u.substring(start, slash) : u.substring(start);
        int at = authority.indexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        if (colon >= 0) {
            authority = authority.substring(0, colon);
        }
        return authority.toLowerCase();
    }
}
