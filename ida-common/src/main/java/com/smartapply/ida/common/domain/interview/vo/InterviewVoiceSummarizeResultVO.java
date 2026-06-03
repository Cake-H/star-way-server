package com.smartapply.ida.common.domain.interview.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * {@code POST .../voice-summarize} 的 {@code data}：AI 总结结果，<b>不入库</b>，由用户核对后再保存。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterviewVoiceSummarizeResultVO {

    /** 无法拆题或需提示用户时的说明 */
    private String hint;

    /** 从口述抽取的公司/岗位/轮次/日期及建议标题 */
    @Builder.Default
    private InterviewSessionMetaDraftVO extractedSession = new InterviewSessionMetaDraftVO();

    /** 一次口述可拆出的多道题（主字段） */
    @Builder.Default
    private List<InterviewQuestionDraftVO> questions = new ArrayList<>();

    /** 兼容旧前端：与 questions[0] 同步 */
    private String question;
    private String myAnswer;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;
}
