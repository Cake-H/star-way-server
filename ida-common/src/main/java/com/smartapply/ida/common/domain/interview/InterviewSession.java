package com.smartapply.ida.common.domain.interview;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartapply.ida.common.mybatis.IdaTableLogic;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 面试复盘场次主表 {@code ida_interview_session}，对应一次面试的整体容器。
 */
@Data
@TableName("ida_interview_session")
public class InterviewSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String companyName;
    private String positionName;
    private String interviewRound;
    private LocalDate interviewAt;
    private Integer questionCount;

    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
