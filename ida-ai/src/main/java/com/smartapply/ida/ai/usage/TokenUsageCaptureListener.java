package com.smartapply.ida.ai.usage;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

/**
 * 注册到所有业务 ChatModel 上的计量监听器：在模型响应时把 token 用量回填到
 * 当前线程的 {@link AiUsageContext}，由业务层统一计费落库。
 * <p>
 * 本类只负责「捕获」，不依赖任何数据库 Bean，保证 ida-ai 模块自洽；
 * 未 {@link AiUsageContext#begin} 的调用（如未接入计量的场景）静默跳过。
 */
@Slf4j
public class TokenUsageCaptureListener implements ChatModelListener {

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        AiUsageContext ctx = AiUsageContext.current();
        if (ctx == null) {
            return;
        }
        try {
            ChatResponse response = responseContext.chatResponse();
            if (response == null || response.metadata() == null) {
                return;
            }
            TokenUsage usage = response.metadata().tokenUsage();
            String model = response.metadata().modelName();
            ctx.capture(usage, model);
            if (usage != null) {
                log.debug("[AI计量] 捕获用量 bizType={} userId={} prompt={} completion={}",
                        ctx.getBizType(), ctx.getUserId(),
                        usage.inputTokenCount(), usage.outputTokenCount());
            }
        } catch (Exception e) {
            // 计量失败绝不能影响业务调用结果
            log.warn("[AI计量] 捕获 token 用量失败: {}", e.getMessage());
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        AiUsageContext ctx = AiUsageContext.current();
        if (ctx != null) {
            log.debug("[AI计量] 模型调用失败，本次不计量 bizType={} userId={} err={}",
                    ctx.getBizType(), ctx.getUserId(),
                    errorContext.error() == null ? "unknown" : errorContext.error().getMessage());
        }
    }
}
