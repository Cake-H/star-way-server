package com.smartapply.ida.module.app.interview.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.interview.InterviewSession;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 复盘场次归属校验：防止通过猜测 {@code sessionId} 读写他人数据。
 */
@Component
@RequiredArgsConstructor
public class InterviewSessionGuard {

    private final InterviewSessionMapper interviewSessionMapper;

    /**
     * @return 未软删且属于 {@code userId} 的场次实体
     * @throws BusinessException 不存在或越权
     */
    public InterviewSession requireOwned(long userId, long sessionId) {
        InterviewSession s = interviewSessionMapper.selectOne(Wrappers.<InterviewSession>lambdaQuery()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getUserId, userId));
        if (s == null) {
            throw new BusinessException("复盘不存在或无权访问");
        }
        return s;
    }
}
