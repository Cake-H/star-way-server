package com.smartapply.ida.ai.plugin.site;

import org.springframework.util.StringUtils;

/**
 * 页面 URL 解析工具：从 pageUrl 提取 host / path，供站点指纹匹配与反馈按站点聚合复用。
 * <p>
 * {@link #extractHost(String)} 提取小写 host（去端口、去 userinfo）；
 * {@link #extractPath(String)} 提取小写 path（去 query、去 fragment，无 path 归一为 {@code "/"}）。
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

    /**
     * 从 url 提取小写 path（去 query、去 fragment），无 path 或无法解析时返回 {@code "/"}。
     * <p>
     * 简易解析，与 {@link #extractHost(String)} 同风格，避免 {@code java.net.URI} 对非标 URL 抛异常。
     * 查找 path 起点前先定位 '?'/'#' 边界，query/fragment 内的斜杠（如
     * {@code https://host?next=/job/1}）不会被误认为 path。
     * 注意：path 中仍可能包含职位 id（如 {@code /job/123}），调用方若需跨职位命中应自行再归一化。
     */
    public static String extractPath(String url) {
        if (!StringUtils.hasText(url)) {
            return "/";
        }
        String u = url.trim();
        int scheme = u.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        // path 终点 = '?' / '#' 中较早者（query/fragment 不参与 path）
        int end = u.length();
        int query = u.indexOf('?', start);
        if (query >= 0 && query < end) {
            end = query;
        }
        int fragment = u.indexOf('#', start);
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        int slash = u.indexOf('/', start);
        if (slash < 0 || slash > end) {
            return "/";
        }
        String path = u.substring(slash, end);
        return path.isEmpty() ? "/" : path.toLowerCase();
    }
}
