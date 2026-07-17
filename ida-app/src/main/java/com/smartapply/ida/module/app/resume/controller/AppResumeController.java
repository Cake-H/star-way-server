package com.smartapply.ida.module.app.resume.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.resume.dto.ResumeSaveDraftRequest;
import com.smartapply.ida.common.domain.resume.dto.ResumeUpdateRequest;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeListItemVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeParseResultVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeSaveDraftResultVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.resume.service.AppResumeDraftService;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import com.smartapply.ida.module.app.resume.service.AppResumeUploadParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 正式客户端 — 简历相关 HTTP 入口。
 * <p>
 * <b>路由分层（给前端对接用）：</b>
 * <ul>
 *   <li><b>解析</b>{@code POST /upload-parse}：上传文件 → 解析为草稿，<b>不落库</b>。</li>
 *   <li><b>列表</b>{@code GET /list}：当前用户简历摘要列表。</li>
 *   <li><b>只读详情</b>{@code GET /{resumeId}}：整份草稿 JSON，用于详情页展示；<b>不要</b>用此接口做保存。</li>
 *   <li><b>新建</b>{@code POST /save-draft}：body 不带 {@code resumeId} 或 id 为空。</li>
 *   <li><b>修改</b>{@code PUT /{resumeId}}：与详情分离，用户点「编辑」后再提交；body 为 {@link ResumeUpdateRequest}，主键仅在路径。</li>
 *   <li><b>删除</b>{@code DELETE /{resumeId}}：软删除（写 {@code deleted_at}）；删默认简历时会自动另选一份为默认（若有）。</li>
 * </ul>
 * <p>
 * <b>路径顺序：</b>必须把 {@code /list} 声明在 {@code /{resumeId}} <i>之前</i>，否则 {@code /list} 会被当成 {@code resumeId=list}。
 * <p>
 * 统一前缀：{@link ApiRouteConstants#API_V1_APP}{@code /resume}。
 */
@Tag(name = "正式客户端-简历")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/resume")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AppResumeController {

    private final AppResumeUploadParseService appResumeUploadParseService;
    private final AppResumeDraftService appResumeDraftService;
    private final AppResumeQueryService appResumeQueryService;

    /**
     * 上传原件并走「OSS + 纯文本抽取 + 大模型结构化」流水线，返回可编辑草稿。
     *
     * @param userId 当前用户（鉴权注入）
     * @param file   表单字段名 {@code file}
     * @return 含 OSS 元信息、解析备注、{@code draft} 结构体；不入库
     */
    @Operation(summary = "上传简历并智能解析（OSS + 方舟大模型，不落库）")
    @PostMapping(value = "/upload-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<ResumeParseResultVO> uploadParse(
            @RequestUserId(required = true) Long userId,
            @RequestParam("file") MultipartFile file) throws Exception {
        log.info("收到上传简历并智能解析申请，/upload-parse userId={} filename={} size={}", userId, file.getOriginalFilename(), file.getSize());
        return ResponseDTO.ok(appResumeUploadParseService.uploadAndParse(userId, file));
    }

    /**
     * 「我的简历」列表：仅摘要字段，用于卡片/列表 UI。
     *
     * @param userId 当前用户
     * @return 按更新时间倒序的列表
     */
    @Operation(summary = "我的简历列表（摘要）")
    @GetMapping("/list")
    public ResponseDTO<List<ResumeListItemVO>> listMyResumes(@RequestUserId(required = true) Long userId) {
        log.debug("收到获取简历列表申请，/list userId={}", userId);
        return ResponseDTO.ok(appResumeQueryService.listMyResumes(userId));
    }

    /**
     * 查看单份简历详情（<b>只读</b>）。
     * <p>
     * 典型前端路径：从列表点进详情页 → 调本接口渲染；若用户点「编辑」，再进入编辑页并可调 {@link #updateResume}。
     *
     * @param userId   当前用户
     * @param resumeId 路径中的简历主键
     * @return 含完整 {@code draft}，结构与解析/保存接口一致
     */
    @Operation(summary = "查看简历详情（只读，用于详情页）")
    @GetMapping("/{resumeId}")
    public ResponseDTO<ResumeDetailVO> getResumeDetail(
            @RequestUserId(required = true) Long userId,
            @PathVariable("resumeId") Long resumeId) {
        log.info("收到查看简历详情申请，resume detail userId={} resumeId={}", userId, resumeId);
        return ResponseDTO.ok(appResumeQueryService.getResumeDetail(userId, resumeId));
    }

    /**
     * 首次保存入库：解析结果或空白草稿在「保存」时调用。
     * <p>
     * {@link ResumeSaveDraftRequest#getResumeId()} 为空或 null 表示新建；若带 id 则与「更新」语义重叠，
     * 更推荐更新走 {@link #updateResume}，避免与 GET 详情混淆。
     *
     * @param userId 当前用户
     * @param body   完整草稿 + 可选标题/默认标记/OSS 追溯字段
     * @return 保存后的简历 id
     */
    @Operation(summary = "保存新简历草稿（POST，body 不带 resumeId 或 resumeId 为空表示新建）")
    @PostMapping(value = "/save-draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<ResumeSaveDraftResultVO> saveDraft(
            @RequestUserId(required = true) Long userId,
            @RequestBody ResumeSaveDraftRequest body) {
        log.info("收到保存新简历草稿申请，/save-draft userId={} resumeId={}", userId, body != null ? body.getResumeId() : null);
        return ResponseDTO.ok(appResumeDraftService.saveDraft(userId, body));
    }

    /**
     * 修改已存在的简历（编辑页提交）。
     * <p>
     * 与 {@link #getResumeDetail} 拆开：只读与写入分离，避免前端误用 GET 做保存或混用 POST body 主键。
     *
     * @param userId   当前用户
     * @param resumeId 路径中的目标简历 id（请求体 {@link ResumeUpdateRequest} 不再重复携带 id）
     * @param body     标题、默认标记、全量 {@code draft} 等
     * @return 成功时仍返回该 {@code resumeId}（与新建接口结构一致）
     */
    @Operation(summary = "修改已有简历（PUT，路径带 resumeId；用于编辑页提交）")
    @PutMapping(value = "/{resumeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<ResumeSaveDraftResultVO> updateResume(
            @RequestUserId(required = true) Long userId,
            @PathVariable("resumeId") Long resumeId,
            @RequestBody ResumeUpdateRequest body) {
        log.info("收到修改已有简历申请，resume update userId={} resumeId={}", userId, resumeId);
        return ResponseDTO.ok(appResumeDraftService.updateResume(userId, resumeId, body));
    }

    /**
     * 软删除简历：列表与详情将不再返回该 id；子表数据保留。
     * <p>
     * 若删除的是默认简历，系统会把「最近更新」的另一份未删简历设为默认（仍存在时）。
     *
     * @param userId   当前用户
     * @param resumeId 路径中的简历 id
     */
    @Operation(summary = "删除简历（软删除）")
    @DeleteMapping("/{resumeId}")
    public ResponseDTO<String> deleteResume(
            @RequestUserId(required = true) Long userId,
            @PathVariable("resumeId") Long resumeId) {
        log.info("收到删除简历申请，resume delete userId={} resumeId={}", userId, resumeId);
        appResumeDraftService.softDeleteResume(userId, resumeId);
        return ResponseDTO.ok();
    }
}
