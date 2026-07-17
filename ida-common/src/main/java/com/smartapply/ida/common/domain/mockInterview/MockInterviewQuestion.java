package com.smartapply.ida.common.domain.mockInterview;

import java.time.LocalDateTime;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.smartapply.ida.common.mybatis.IdaTableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 模拟面试题目(MockInterviewQuestion)表实体类
 *
 * @author makejava
 * @since 2026-07-12 17:45:52
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_mock_interview_question")
public class MockInterviewQuestion {
    //题目 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    //关联模拟面试 ID
    private Long sessionId;
    //题目顺序，从 1 开始
    private Integer sortOrder;
    //问题内容
    private String question;
    //参考答案
    private String referenceAnswer;
    //答题要点/评价维度
    private String answerEvaluation;
    //补充说明
    private String otherNotes;

    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
