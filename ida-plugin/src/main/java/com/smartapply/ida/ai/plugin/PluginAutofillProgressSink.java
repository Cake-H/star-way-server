package com.smartapply.ida.ai.plugin;

import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillProgressPhase;

/**
 * 填表计划生成过程中的进度回调：由 {@link PluginFormAutofillAiService} 在关键步骤调用，
 * 同步接口可传入 {@link #noop()} 忽略；异步任务则挂接 {@link PluginAutofillTaskRegistry} 以更新任务快照并打日志。
 * <p>
 * <b>线程模型：</b>回调在调用 {@code planAutofill} 的线程上执行（同步请求线程或线程池中的异步 worker），
 * 实现类应避免在回调里做耗时 IO，只做内存状态更新与日志。
 */
@FunctionalInterface
public interface PluginAutofillProgressSink {

    /**
     * @param phase   当前所处阶段，映射到 {@link PluginAutofillProgressPhase}
     * @param detail  供控制台/排障的补充说明（中文短句即可）
     * @param percent 0～100 的近似完成度，供扩展进度条；不要求严格单调
     */
    void onProgress(PluginAutofillProgressPhase phase, String detail, int percent);

    /**
     * 同步 {@code POST /autofill-plan} 使用：空实现，避免向不存在的「任务表」写进度，
     * 也使 planAutofill 内无需写 if (async) 分支。
     */
    static PluginAutofillProgressSink noop() {
        // 三参数全忽略：同步 HTTP 请求无任务表，也不打进度日志（避免刷屏）
        return (phase, detail, percent) -> {
        };
    }
}
