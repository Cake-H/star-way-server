package com.smartapply.ida.module.admin.feedback.service;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.feedback.dto.AppFeedbackStatusUpdateRequest;
import com.smartapply.ida.common.domain.feedback.vo.AdminFeedbackVO;

/**
 * 管理后台用户反馈服务：反馈分页列表/详情、处理状态流转。
 */
public interface AdminFeedbackService {

    /**
     * 反馈分页列表（按提交时间倒序）。
     *
     * @param page    页码（从 1 开始）
     * @param size    每页大小（上限 100）
     * @param status  处理状态 code 精确匹配（见 {@code AppFeedbackStatus}），可空
     * @param keyword 关键字（反馈内容/联系邮箱模糊匹配），可空
     */
    PageVO<AdminFeedbackVO> pageFeedbacks(long page, long size, String status, String keyword);

    /** 反馈详情：附件 objectKey 换成预签名 URL（OSS 未启用时 url 为空串）。 */
    AdminFeedbackVO getFeedbackDetail(Long feedbackId);

    /** 更新处理状态与处理备注。 */
    void updateFeedbackStatus(Long feedbackId, AppFeedbackStatusUpdateRequest req);
}
