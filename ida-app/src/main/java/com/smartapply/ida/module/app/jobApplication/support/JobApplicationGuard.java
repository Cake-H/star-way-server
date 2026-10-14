package com.smartapply.ida.module.app.jobApplication.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.jobApplication.JobApplication;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.jobApplication.mapper.JobApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 投递记录归属校验：防止通过猜测 {@code applicationId} 读写他人数据。
 */
@Component
@RequiredArgsConstructor
public class JobApplicationGuard {

    private final JobApplicationMapper jobApplicationMapper;

    /**
     * @return 未软删且属于 {@code userId} 的投递记录实体
     * @throws BusinessException 不存在或越权
     */
    public JobApplication requireOwned(long userId, long applicationId) {
        JobApplication a = jobApplicationMapper.selectOne(Wrappers.<JobApplication>lambdaQuery()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId));
        if (a == null) {
            throw new BusinessException("投递记录不存在或无权访问");
        }
        return a;
    }
}
