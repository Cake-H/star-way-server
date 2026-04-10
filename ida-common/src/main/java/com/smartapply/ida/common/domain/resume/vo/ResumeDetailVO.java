package com.smartapply.ida.common.domain.resume.vo;

import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 简历详情（只读查看）：结构与保存草稿时使用的 {@link ResumeParseDraftDto} 对齐，
 * 便于前端「查看页」与「编辑页」复用同一套表单模型；编辑提交走 {@link com.smartapply.ida.common.domain.resume.dto.ResumeUpdateRequest}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDetailVO {

    private Long resumeId;

    private String title;

    private Integer isDefault;

    /** 主表结构版本，便于前后端做兼容分支 */
    private Integer schemaVersion;

    private LocalDateTime updatedAt;

    /**
     * 完整结构化内容（主表字段 + 各子表列表），与上传解析返回的 draft 形态一致。
     */
    private ResumeParseDraftDto draft;
}
