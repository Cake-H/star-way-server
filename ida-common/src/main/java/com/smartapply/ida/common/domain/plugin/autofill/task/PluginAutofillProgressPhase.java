package com.smartapply.ida.common.domain.plugin.autofill.task;

/**
 * 一键填表异步任务在服务端经历的阶段，用于前端轮询展示进度条/步骤文案，并与控制台日志一一对应。
 * <p>
 * <b>设计说明：</b>大模型调用（{@link #CALLING_MODEL}）往往占全程绝大部分时间；当前版本未对接方舟流式 token，
 * 因此该阶段无法按 token 细分，只能给出「等待模型」的粗粒度进度；后续若接入 SSE，可再拆分子阶段。
 * 与代码注释、运维说明对照：{@code docs/plugin-autofill-async-architecture.md}。
 */
public enum PluginAutofillProgressPhase {

    /** 请求已入队，尚未开始执行业务逻辑 */
    RECEIVED,

    /** 正在从数据库加载简历草稿并序列化为 JSON */
    LOADING_RESUME,

    /** 正在组装系统提示词、用户消息（简历 + 页面控件快照） */
    BUILDING_PROMPT,

    /**
     * 正在等待火山方舟 HTTP 返回（含思考链时可能数十秒～数分钟）。
     * 阻塞的是「执行 planAutofill 的当前线程」：异步路径为 {@code plugin-autofill-*} 线程池线程，同步路径为 Tomcat worker。
     */
    CALLING_MODEL,

    /** 已收到模型文本，正在解析为结构化 {@link com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO} */
    PARSING_MODEL_JSON,

    /** 正在做 selector 白名单、锚点合并、blockedFields 等安全清洗 */
    SANITIZING_PLAN,

    /** 整条流水线成功结束，{@link PluginAutofillTaskStatusVO#getResult()} 可用 */
    COMPLETED,

    /** 任意步骤抛出业务异常或未捕获异常，{@link PluginAutofillTaskStatusVO#getErrorMessage()} 可用 */
    FAILED
}
