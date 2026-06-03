package com.smartapply.ida.common.domain.interview;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartapply.ida.common.mybatis.IdaTableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单场复盘下的一条结构化问答记录 {@code ida_interview_question}。
 */
@Data
@TableName("ida_interview_question")
public class InterviewQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer sortOrder;
    private String question;
    private String myAnswer;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;

    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
