package com.smartapply.ida.module.app.jobApplication.service;

import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationCreateRequest;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationQueryRequest;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationUpdateRequest;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationIdVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationListItemVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationProgressOptionVO;

import java.util.List;

/**
 * 投递记录业务：用户手动维护的投递笔记本 CRUD，列表支持按公司/岗位/日期/进度组合筛选。
 */
public interface AppJobApplicationService {

    /**
     * 当前用户的投递记录列表（未软删），按投递日期倒序。
     * <p>
     * 各筛选条件均可选、可组合：公司/岗位为模糊匹配，进度为精确匹配，日期为闭区间。
     */
    List<JobApplicationListItemVO> listApplications(long userId, JobApplicationQueryRequest query);

    JobApplicationIdVO createApplication(long userId, JobApplicationCreateRequest req);

    void updateApplication(long userId, long applicationId, JobApplicationUpdateRequest req);

    void deleteApplication(long userId, long applicationId);

    /** 投递进度下拉选项（code + 中文文案），避免前端硬编码枚举。 */
    List<JobApplicationProgressOptionVO> listProgressOptions();
}
