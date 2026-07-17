package com.smartapply.ida.module.app.mockInterview.service;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.mockInterview.dto.MockInterviewCreateDTO;
import com.smartapply.ida.common.domain.mockInterview.dto.MockInterviewUpdateProgressDTO;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewDetailVO;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewListItemVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface MockInterviewService {
    List<MockInterviewListItemVO> getMockInterviewList(long userId);


    MockInterviewDetailVO getMockInterviewDetail(Long userId, Long sessionId);

    Long createMockInterview(Long userId, MockInterviewCreateDTO dto);

    void deleteMockInterview(Long userId, Long sessionId);

    void updateMockInterviewStatus(Long userId, Long sessionId, String status);

    void saveMockInterviewProgress(Long userId, Long sessionId, MockInterviewUpdateProgressDTO progressDto);
}
