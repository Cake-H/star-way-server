package com.smartapply.ida.common.domain.resume.dto;

import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.Data;

/**
 * 保存简历草稿：将前端编辑后的 {@link ResumeParseDraftDto} 落库到 {@code ida_resume} 及子表。
 */
@Data
public class ResumeSaveDraftRequest {

    /**
     * 不传或 null 表示新建简历；传入则更新（须属于当前用户且未删除）。
     */
    private Long resumeId;

    /** 简历名称，可空则沿用已有或默认「我的简历」 */
    private String title;

    /** 是否默认简历：0/1；传 1 时会将该用户其它简历的默认标记清零 */
    private Integer isDefault;

    /** 必填：结构化草稿（与上传解析返回的 draft 结构一致） */
    private ResumeParseDraftDto draft;

    /** 可选：解析来源元信息，写入 {@code ida_resume.extra_json} 供追溯 */
    private String originalFilename;
    private String ossBucket;
    private String ossObjectKey;
}
