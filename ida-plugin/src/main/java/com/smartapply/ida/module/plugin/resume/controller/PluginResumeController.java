package com.smartapply.ida.module.plugin.resume.controller;

import com.smartapply.ida.ai.plugin.PluginFormAutofillAiService;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackStatVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillResumeContextVO;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillTaskSubmitResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillTaskStatusVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeListItemVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import com.smartapply.ida.module.plugin.resume.autofill.PluginAutofillFeedbackService;
import com.smartapply.ida.module.plugin.resume.autofill.PluginAutofillFeedbackStatsService;
import com.smartapply.ida.module.plugin.resume.autofill.PluginAutofillTaskService;
import com.smartapply.ida.module.plugin.resume.autofill.ResumePreloadCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 浏览器扩展 — 简历：列表、只读预览、AI 一键填表计划。
 * <p>
 * <b>与正式客户端 {@code /api/v1/app/resume} 的关系：</b>列表与详情复用同一套读服务，保证数据一致；插件端不提供修改/删除，
 * 避免在扩展里误触写接口。
 * <p>
 * <b>一键填表与前端（扩展）配合：</b>
 * <ul>
 *   <li>扩展在 content script 中扫描当前页 {@code input}/{@code textarea}/{@code select} 等，生成稳定 {@code selector} 与
 *       {@link com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto} 列表；</li>
 *   <li>调用 {@code POST .../autofill-plan} 上传 {@code resumeId} + 快照，服务端用大模型生成 {@link PluginAutofillPlanResultVO}；</li>
 *   <li>若 {@code fillable=false}，展示 {@code hint}，并可展示 {@code blockedFields}；若为 true，扩展按 {@code assignments}（含 {@code order}、
 *       {@code confidence}/{@code matchType}、{@code subAssignments}/{@code targetRole}、锚点字段）对 DOM 赋值；{@code blockedFields} 标红「需人工」项。</li>
 * </ul>
 * 服务端无法直接操作标签页 DOM，故<b>必须</b>由扩展完成采集与回写。
 * <p>
 * <b>路由顺序：</b>{@code /list}、{@code /{resumeId}/autofill-context}、{@code /autofill-plan}、{@code /autofill-plan/tasks} 必须写在 {@code /{resumeId}} 之前，
 * 否则路径变量可能被误匹配。
 */
@Tag(name = "插件端-简历")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_PLUGIN + "/resume")
@Slf4j
@Validated
@RequiredArgsConstructor
public class PluginResumeController {

    /**
     * 简历列表与详情：来自 ida-app 模块，供插件只读拉取用户简历数据。
     */
    private final AppResumeQueryService appResumeQueryService;
    /**
     * 一键填表核心：调大模型生成 {@link PluginAutofillPlanResultVO}；
     * 同步接口直接调 {@link PluginFormAutofillAiService #planAutofill(long, PluginAutofillPlanRequest)}。
     */
    private final PluginFormAutofillAiService pluginFormAutofillAiService;
    /**
     * 异步任务：注册 taskId、往线程池投递 Runnable、供 GET 轮询读内存快照；与 {@link #pluginFormAutofillAiService} 配合使用。
     */
    private final PluginAutofillTaskService pluginAutofillTaskService;
    /**
     * 反馈服务：异步写入 Redis，不影响主流程。
     */
    private final PluginAutofillFeedbackService pluginAutofillFeedbackService;
    /**
     * 反馈统计：用户级命中率与失败原因/字段/站点聚合，驱动模板校准与同义词扩充。
     */
    private final PluginAutofillFeedbackStatsService pluginAutofillFeedbackStatsService;
    /**
     * 简历预加载缓存：autofill-context 请求时提前将简历写入 Redis，planAutofill 直接读取。
     */
    private final ResumePreloadCacheService resumePreloadCacheService;

    @Operation(summary = "我的简历列表（摘要，与正式客户端列表字段一致）")
    @GetMapping("/list")
    public ResponseDTO<List<ResumeListItemVO>> listMyResumes(@RequestUserId(required = true) Long userId) {
        log.debug("收到插件端获取简历列表请求，plugin resume list userId={}", userId);
        // ResponseDTO.ok：统一包装 code=0，业务数据放在 data
        return ResponseDTO.ok(appResumeQueryService.listMyResumes(userId));
    }

    /**
     * 一键填表前置：返回云端简历各可重复模块的条目数，扩展应在采快照前按该数字点击「+添加」展开块，使 DOM 与简历条数对齐。
     * <p>
     * 与 {@link PluginFormAutofillAiService} 注入提示词 PART_C 的统计来源一致（同一 Extractor）。
     */
    @Operation(summary = "一键填表前置：云简历各模块条目数（用于展开网申表单块）")
    @GetMapping("/{resumeId}/autofill-context")
    public ResponseDTO<PluginAutofillResumeContextVO> getAutofillResumeContext(
            @RequestUserId(required = true) Long userId,
            @PathVariable("resumeId") Long resumeId) {
        log.info("收到各模块条目数获取申请，/autofill-context userId={} resumeId={}", userId, resumeId);
        // 只读、无方舟调用；与预览详情共享鉴权
        PluginAutofillResumeContextVO context = appResumeQueryService.getAutofillResumeContext(userId, resumeId);

        // 顺带预加载简历到 Redis：planAutofill 时直接从缓存读取，减少 DB 查询延迟
        try {
            ResumeDetailVO detail = appResumeQueryService.getResumeDetail(userId, resumeId);
            resumePreloadCacheService.preload(userId, resumeId, detail);
        } catch (Exception e) {
            // 预加载失败不影响 autofill-context 的正常返回
            log.warn("简历预加载失败，planAutofill 将回退查 DB: {}", e.getMessage());
        }

        return ResponseDTO.ok(context);
    }

    @Operation(summary = "预览简历详情（只读，不含保存/修改语义）")
    @GetMapping("/{resumeId}")
    public ResponseDTO<ResumeDetailVO> previewResume(
            @RequestUserId(required = true) Long userId,
            @PathVariable("resumeId") Long resumeId) {
        log.info("收到插件端预览简历详情请求，plugin resume preview userId={} resumeId={}", userId, resumeId);
        return ResponseDTO.ok(appResumeQueryService.getResumeDetail(userId, resumeId));
    }

    /**
     * AI 根据简历与当前页控件快照生成填值计划；不可填页面由模型或本地规则给出 {@link PluginAutofillPlanResultVO#getHint()}。
     * <p>
     * <b>同步模式：</b>本方法在 <b>Tomcat 工作线程</b> 内一直阻塞到方舟返回；大模型耗时长时易导致浏览器/网关超时，
     * 详见文档 {@code docs/plugin-autofill-async-architecture.md}；需要快速返回 HTTP 时请改用 {@link #submitAutofillPlanTask}。
     */
    @Operation(summary = "一键填表：生成填值计划（assignments + blockedFields + 锚点/复合字段元数据，需扩展采集页面并回写 DOM）")
    @PostMapping(value = "/autofill-plan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<PluginAutofillPlanResultVO> autofillPlan(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody PluginAutofillPlanRequest body) {
        log.info("收到插件端一键填表请求，plugin autofill-plan userId={} resumeId={} fieldCount={}",
                userId, body.getResumeId(),
                body.getPage() != null && body.getPage().getFields() != null ? body.getPage().getFields().size() : 0);
        // 同步路径：内部使用 progress=noop，不更新异步任务表
        return ResponseDTO.ok(pluginFormAutofillAiService.planAutofill(userId, body));
    }

    /**
     * 异步一键填表：立即返回 {@link PluginAutofillTaskSubmitResultVO#getTaskId()}，扩展端轮询
     * {@link #getAutofillPlanTask(long, String)} 直至 status 为终态；服务端在专用线程池执行模型调用，避免网关/浏览器同步超时。
     * <p>6
     * <b>与同步接口差异：</b>此处 HTTP 很快结束；真正调大模型在 {@link PluginAutofillTaskService}
     * 投递到 {@code pluginAutofillExecutor} 的后台线程中执行（{@code ThreadPoolTaskExecutor#execute} 不等待 Runnable 跑完）。
     */
    @Operation(summary = "一键填表（异步）：提交任务，返回 taskId 与轮询路径模板")
    @PostMapping(value = "/autofill-plan/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<PluginAutofillTaskSubmitResultVO> submitAutofillPlanTask(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody PluginAutofillPlanRequest body) {
        log.info("收到插件端异步一键填表提交，task submit userId={} resumeId={} fieldCount={}",
                userId, body.getResumeId(),
                body.getPage() != null && body.getPage().getFields() != null ? body.getPage().getFields().size() : 0);
        // 仅提交任务：内部 register + executor.execute(...)，本线程不阻塞在方舟上
        return ResponseDTO.ok(pluginAutofillTaskService.submitTask(userId, body));
    }

    /**
     * 查询异步填表任务状态；RUNNING/PENDING 时应继续轮询（建议间隔 500ms～2s），SUCCEEDED 时读取 {@link PluginAutofillTaskStatusVO#getResult()}。
     * <p>
     * <b>数据来源：</b>读进程内 {@link com.smartapply.ida.module.plugin.resume.autofill.PluginAutofillTaskRegistry} 的快照，
     * 未使用 Redis；多实例部署时须改为共享存储，否则轮询可能打到无此 taskId 的节点（见架构文档）。
     */
    @Operation(summary = "一键填表（异步）：按 taskId 查询进度与结果")
    @GetMapping("/autofill-plan/tasks/{taskId}")
    public ResponseDTO<PluginAutofillTaskStatusVO> getAutofillPlanTask(
            @RequestUserId(required = true) Long userId,
            @PathVariable("taskId") String taskId) {
        // 短请求：只读内存 Map + userId 校验，不调用方舟
        return ResponseDTO.ok(pluginAutofillTaskService.getTaskStatus(userId, taskId));
    }

    /**
     * 前端填写完成后上报逐字段反馈（命中/失败/实际值），异步写入 Redis 队列，
     * 用于后续统计匹配率和迭代优化 {@link com.smartapply.ida.ai.plugin.deterministic.ChineseFieldAliasTable}。
     */
    @Operation(summary = "一键填表：上报字段填写反馈（异步入队，不阻塞）")
    @PostMapping(value = "/autofill-plan/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<Void> submitAutofillFeedback(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody PluginAutofillFeedbackRequest body) {
        log.info("收到填表反馈 userId={} resumeId={} fieldCount={}",
                userId, body.getResumeId(),
                body.getFields() != null ? body.getFields().size() : 0);
        pluginAutofillFeedbackService.submitFeedback(userId, body);
        return ResponseDTO.ok(null);
    }

    /**
     * 用户级填表反馈统计：近 N 天命中率与 Top 失败原因/字段/站点，用于定位「哪类字段最常卡住」。
     * 全局运营统计由后台管理系统提供，不在本接口。
     */
    @Operation(summary = "一键填表：我的填写反馈统计（命中率/失败原因/失败字段/站点，近 N 天）")
    @GetMapping("/autofill-feedback/stats")
    public ResponseDTO<PluginAutofillFeedbackStatVO> getAutofillFeedbackStats(
            @RequestUserId(required = true) Long userId,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "siteHost", required = false) String siteHost) {
        return ResponseDTO.ok(pluginAutofillFeedbackStatsService.stats(userId, days, siteHost));
    }
}
