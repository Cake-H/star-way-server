package com.smartapply.ida.common.domain.resume.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存简历草稿成功后的返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSaveDraftResultVO {

    private Long resumeId;
}
