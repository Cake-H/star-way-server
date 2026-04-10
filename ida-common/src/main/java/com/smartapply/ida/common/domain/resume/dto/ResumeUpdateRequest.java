package com.smartapply.ida.common.domain.resume.dto;

import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.Data;

/**
 * 修改已入库的简历：仅用于 {@code PUT /resume/{resumeId}}，主键由路径给出，故<b>不包含</b> {@code resumeId} 字段，
 * 避免与 URL 歧义；语义上与 {@link ResumeSaveDraftRequest} 除主键外一致。
 */
@Data
public class ResumeUpdateRequest {

    private String title;

    private Integer isDefault;

    /** 必填：与保存草稿时相同的全量草稿结构 */
    private ResumeParseDraftDto draft;

    /** 可选：若本次编辑关联新的原件上传，可再次写入 extra_json.lastUpload */
    private String originalFilename;
    private String ossBucket;
    private String ossObjectKey;
}
