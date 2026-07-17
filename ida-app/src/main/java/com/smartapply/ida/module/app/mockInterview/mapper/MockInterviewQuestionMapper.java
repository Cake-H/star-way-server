package com.smartapply.ida.module.app.mockInterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.mockInterview.MockInterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MockInterviewQuestionMapper extends BaseMapper<MockInterviewQuestion> {
}
