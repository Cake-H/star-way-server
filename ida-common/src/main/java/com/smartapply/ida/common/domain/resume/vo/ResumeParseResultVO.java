package com.smartapply.ida.common.domain.resume.vo;

import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历上传 OSS 后，经「纯文本抽取 + 大模型结构化」得到的统一响应。
 * <p>
 * 说明：本对象表示「草稿」，不保证与简历原件 100% 一致；前端应允许用户逐字段修改后再落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParseResultVO {

    /** 原始上传文件名（客户端提供的，仅作展示） */
    private String originalFilename;

    /** 推断的 MIME，如 application/pdf */
    private String contentType;

    /** OSS 存储桶名（便于排查与后续生成签名 URL） */
    private String ossBucket;

    /**
     * OSS 对象键（路径），例如 {@code app/resume/12/uuid.pdf}；
     * 私有桶时客户端不能直连访问，需后续接「临时下载链接」接口。
     */
    private String ossObjectKey;

    /**
     * 若 Bucket 为公共读或已拼接静态域名，则给出可浏览器访问的 URL；否则为 null。
     */
    private String ossPublicUrl;

    /** 抽取出的纯文本字符数（送入模型前可能已截断，见 {@link #parseNotes}） */
    private int plainTextCharLength;

    /**
     * 解析过程提示：如「文本过长已截断」「未识别到教育经历」等，供前端展示，非错误。
     */
    @Builder.Default
    private List<String> parseNotes = new ArrayList<>();

    /** 大模型抽取并映射到本系统字段后的草稿（子表为列表结构） */
    private ResumeParseDraftDto draft;
}
