package com.smartapply.ida.common.constant;

/**
 * 插件侧 Redis 缓存键常量，供 ida-plugin（写入）与 ida-app（失效）共用。
 * <p>
 * 放在 ida-common 是因为 ida-app 不依赖 ida-plugin，避免反向依赖。
 */
public final class PluginCacheConstants {

    private PluginCacheConstants() {}

    /**
     * 简历预加载缓存键前缀，完整键为 {@code 前缀 + userId + ":" + resumeId}。
     * 与 {@code ResumePreloadCacheService}（ida-plugin）保持一致，简历写路径按此键主动失效。
     */
    public static final String RESUME_PRELOAD_KEY_PREFIX = "ida:plugin:resume:preload:";

    /**
     * 拼接简历预加载缓存完整键。
     */
    public static String resumePreloadKey(Long userId, Long resumeId) {
        return RESUME_PRELOAD_KEY_PREFIX + userId + ":" + resumeId;
    }
}
