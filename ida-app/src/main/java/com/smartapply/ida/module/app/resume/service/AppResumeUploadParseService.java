package com.smartapply.ida.module.app.resume.service;

import com.smartapply.ida.common.domain.resume.vo.ResumeParseResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * App 端：简历上传 → OSS 存档 → 纯文本抽取 → 方舟大模型结构化，返回可编辑草稿。
 * <p>
 * <b>不落库</b>：解析结果仅封装在 {@link ResumeParseResultVO} 中；用户确认后调用
 * {@link AppResumeDraftService#saveDraft(long, com.smartapply.ida.common.domain.resume.dto.ResumeSaveDraftRequest)} 写入数据库。
 * <p>
 * 实现类见 {@link com.smartapply.ida.module.app.resume.service.impl.AppResumeUploadParseSeviceImpl}。
 */
public interface AppResumeUploadParseService {

    /**
     * 同步执行上传与解析；可能因 OSS/抽取/大模型调用抛出受检异常，由全局异常处理或上层转换。
     *
     * @param userId 当前登录用户，用于 OSS 路径隔离
     * @param file   原始简历文件（pdf/docx 等）
     * @return 含 OSS 元数据、解析备注与 {@code draft} 的 VO
     */
    ResumeParseResultVO uploadAndParse(long userId, MultipartFile file) throws Exception;
}
