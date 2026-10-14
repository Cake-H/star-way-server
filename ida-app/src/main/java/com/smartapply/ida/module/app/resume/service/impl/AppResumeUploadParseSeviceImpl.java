package com.smartapply.ida.module.app.resume.service.impl;

import com.smartapply.ida.ai.langchain4j.config.LangChain4jProperties;
import com.smartapply.ida.ai.resume.ResumeArkStructuringService;
import com.smartapply.ida.common.domain.resume.vo.ResumeParseResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.executor.ResumeParseExecutorConfig;
import com.smartapply.ida.integration.oss.AliyunOssStorageService;
import com.smartapply.ida.integration.resume.ResumePlainTextExtractor;
import com.smartapply.ida.module.app.resume.service.AppResumeUploadParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * App 端：简历上传 → OSS 存档 → 本地抽取纯文本 → 大模型结构化。
 * <p>
 * <b>职责边界：</b>本类只做「解析链路」，<b>不写数据库</b>；用户在前端改完草稿后，由
 * {@link com.smartapply.ida.module.app.resume.service.AppResumeDraftService#saveDraft(long, com.smartapply.ida.common.domain.resume.dto.ResumeSaveDraftRequest)}
 * 落库 {@code ida_resume} 及子表。
 * <p>
 * <b>流水线说明：</b>
 * <ol>
 *   <li>OSS：原件按用户隔离路径上传，便于审计与后续重新解析；若未启用 OSS Bean，直接业务异常提示配置。</li>
 *   <li>纯文本：在应用进程内从 {@link MultipartFile} 抽取，避免再经 OSS 下载浪费流量与延迟。</li>
 *   <li>长度：超过 {@code ida.langchain4j.resume-parse.max-plain-text-chars} 时截断并记入 {@code parseNotes}，防止超大正文拖垮模型或超时。</li>
 *   <li>大模型：调用 LangChain4j AI Service，将返回 JSON 反序列化为 {@link com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto}。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppResumeUploadParseSeviceImpl implements AppResumeUploadParseService {

    /** 条件装配下可能不存在，故用 ObjectProvider 避免启动失败 */
    private final ObjectProvider<AliyunOssStorageService> ossStorageProvider;
    private final ResumePlainTextExtractor plainTextExtractor;
    private final ResumeArkStructuringService arkStructuringService;
    private final LangChain4jProperties langChain4jProperties;

    @Qualifier(ResumeParseExecutorConfig.BEAN_NAME)
    private final ThreadPoolTaskExecutor resumeParseExecutor;

    /**
     * 同步执行完整解析链路，返回可供前端编辑的草稿 VO（含 OSS 元信息、解析备注与草稿 DTO）。
     *
     * @param userId 当前登录用户，参与 OSS 对象键路径隔离
     * @param file   用户上传的原始文件（如 pdf/docx）
     */
    @Override
    public ResumeParseResultVO uploadAndParse(long userId, MultipartFile file) throws Exception {
        AliyunOssStorageService oss = ossStorageProvider.getIfAvailable();
        if (oss == null) {
            throw new BusinessException("未启用阿里云 OSS：请在配置中设置 ida.aliyun.oss.enabled=true 并填写 endpoint、bucket、AK/SK");
        }

        // 步骤 1：原件上传 OSS，与「解析结果」分离存储，满足追溯与合规要求
        // AliyunOssStorageService.OssUploadResult put = oss.uploadResume(file, userId);
        CompletableFuture<AliyunOssStorageService.OssUploadResult> ossFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return oss.uploadResume(file, userId);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, resumeParseExecutor);

        // 步骤 2：同一次请求内从内存中的 MultipartFile 抽文本，避免「上传后再从 OSS 拉回」的二次 IO
        // String plain = plainTextExtractor.extract(file);
        CompletableFuture<String> plainFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return plainTextExtractor.extract(file);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, resumeParseExecutor);

        AliyunOssStorageService.OssUploadResult put;
        String plain;
        try {
            put = ossFuture.join();
            plain = plainFuture.join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();  // 剥掉外壳，取出原始异常
            if (cause instanceof BusinessException be) {
                throw be; // 业务异常原样抛，前端能看到正确文案
            }
            if (cause instanceof Exception e) {
                throw e; // 方法签名有 throws Exception，直接抛出原始异常
            }
            throw ce; // 其他异常也原样抛出
        }



        // 步骤 3：按配置截断过长正文，并把原因写入 parseNotes，供前端提示用户补全
        List<String> notes = new ArrayList<>();
        int maxChars = langChain4jProperties.getResumeParse().getMaxPlainTextChars();
        if (plain.length() > maxChars) {
            notes.add("简历纯文本过长，已按配置截断前 " + maxChars + " 字符再送入模型，建议手动补全未展示部分");
            plain = plain.substring(0, maxChars);
        }

        // 步骤 4：调用大模型（AI Service 层已内置额度守卫 + 用量计量）
        var draft = arkStructuringService.structure(userId, plain);

        // 日志不落全文，仅记录长度与 OSS 键，降低隐私泄露风险
        log.info("简历解析完成 userId={} ossKey={} plainLen={}", userId, put.objectKey(), plain.length());

        // 隐私（1.4 基线）：Bucket 私有读，只向前端签发 1 小时有效的预签名 URL，禁止公开直链
        String signedUrl = oss.generatePresignedUrl(put.objectKey(), Duration.ofHours(1));

        return ResumeParseResultVO.builder()
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .ossBucket(put.bucket())
                .ossObjectKey(put.objectKey())
                .ossSignedUrl(signedUrl)
                .plainTextCharLength(plain.length())
                .parseNotes(notes)
                .draft(draft)
                .build();
    }
}
