package com.smartapply.ida.common.domain.mockInterview;

import java.time.LocalDateTime;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.smartapply.ida.common.mybatis.IdaTableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
/**
 * 模拟面试(MockInterview)表实体类
 *
 * @author makejava
 * @since 2026-07-12 17:42:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_mock_interview_session")
public class MockInterviewSession {
    //模拟面试 ID
    @TableId(type = IdType.AUTO)
    private Long id;

    //所属用户 ID
    private Long userId;
    //关联简历 ID
    private Long resumeId;
    //标题，如「Java 后端开发 · 模拟面试」
    private String title;
    //状态：GENERATED/PRACTICING/COMPLETED
    private String status;
    //题目数量
    private Integer questionCount;
    //当前练习到的题目 ID
    private Long currentQuestionId;
    //当前题目答案是否已展开：0/1
    private Integer answerRevealed;

    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
