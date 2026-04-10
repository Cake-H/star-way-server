package com.smartapply.ida.common.domain.resume.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 「我的简历」列表中单条摘要，用于卡片/表格展示，字段刻意精简以减轻列表接口负载。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeListItemVO {

    /** 简历主键，用于跳转详情或编辑 */
    private Long resumeId;

    /** 展示用标题 */
    private String title;

    /** 是否默认简历：0/1 */
    private Integer isDefault;

    /** 姓名预览（来自主表 full_name，可为空） */
    private String fullName;

    /** 最近更新时间，列表排序依据 */
    private LocalDateTime updatedAt;
}
