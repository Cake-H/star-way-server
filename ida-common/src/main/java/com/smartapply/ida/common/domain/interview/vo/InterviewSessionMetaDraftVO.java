package com.smartapply.ida.common.domain.interview.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 从口述中抽取的场次元信息草稿；空串表示口述未提及，非 null。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterviewSessionMetaDraftVO {
    private String companyName;
    private String positionName;
    private String interviewRound;
    /** YYYY-MM-DD */
    private String interviewAt;
    /** 建议列表标题，如「字节跳动 · 后端开发 · 一面」 */
    private String suggestedTitle;
}
