package com.smartapply.ida.module.app.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.interview.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

/** 表 {@code ida_interview_question} */
@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
