package com.smartapply.ida.ai.usage;

import dev.langchain4j.model.output.TokenUsage;

/**
 * 一次 LLM 业务调用的计量上下文（ThreadLocal）。
 * <p>
 * 业务层在调用 Assistant 前 {@link #begin(Long, String, Long)}，
 * 由 {@link TokenUsageCaptureListener} 在模型响应时回填 token 用量，
 * 业务层在调用结束后读取 {@link #getCapturedUsage()} 进行计费落库，最后必须 {@link #clear()}。
 * <p>
 * <b>线程模型：</b>同步 Web 请求与异步任务（如插件填表任务线程）均在「发起 LLM 调用的同一线程」
 * 内完成 begin → call → read → clear，不使用跨线程传递。
 */
public final class AiUsageContext {

    private static final ThreadLocal<AiUsageContext> HOLDER = new ThreadLocal<>();

    private final Long userId;
    private final String bizType;
    private final Long bizId;
    private TokenUsage capturedUsage;
    private String capturedModel;

    private AiUsageContext(Long userId, String bizType, Long bizId) {
        this.userId = userId;
        this.bizType = bizType;
        this.bizId = bizId;
    }

    /** 开始一次计量上下文；嵌套/重复调用会覆盖旧值，调用方须保证配对 clear */
    public static void begin(Long userId, String bizType, Long bizId) {
        HOLDER.set(new AiUsageContext(userId, bizType, bizId));
    }

    /** 当前上下文；未 begin 时返回 null（监听器据此静默跳过，不影响未接入计量的调用） */
    public static AiUsageContext current() {
        return HOLDER.get();
    }

    /** 清除上下文，防止线程复用导致串号（务必在 finally 中调用） */
    public static void clear() {
        HOLDER.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public String getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    /** 由监听器在模型响应时调用，业务层不应直接调用 */
    public void capture(TokenUsage tokenUsage, String model) {
        this.capturedUsage = tokenUsage;
        this.capturedModel = model;
    }

    /** 模型返回的 token 用量；调用失败或模型未返回时为 null */
    public TokenUsage getCapturedUsage() {
        return capturedUsage;
    }

    /** 模型实际返回的模型 ID，可能为 null */
    public String getCapturedModel() {
        return capturedModel;
    }
}
