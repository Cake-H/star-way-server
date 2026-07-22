package com.smartapply.ida.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * API 路径常量：双端隔离，避免插件与桌面客户端路由混用导致鉴权策略难以拆分。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiRouteConstants {

    /** 浏览器扩展调用的 API 前缀 */
    public static final String API_V1_PLUGIN = "/api/v1/plugin";

    /** 正式客户端（桌面应用等）调用的 API 前缀 */
    public static final String API_V1_APP = "/api/v1/app";

    /** 管理后台调用的 API 前缀（独立进程 ida-admin-server，与 C 端隔离部署） */
    public static final String API_V1_ADMIN = "/api/v1/admin";
}
