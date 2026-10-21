package com.smartapply.ida.module.app.feedback.service;

import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackIdVO;
import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackListItemVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * APP 内用户反馈业务：提交反馈（可带图片/视频附件，附件存 OSS）、查询当前用户反馈列表。
 */
public interface AppFeedbackService {

    /**
     * 提交反馈：先传 OSS 再落库，初始状态 PENDING。
     *
     * @param userId       当前登录用户，参与 OSS 对象键路径隔离
     * @param content      反馈内容（必填，上限 2000 字，Controller 已做注解校验）
     * @param contactEmail 联系邮箱（可空）
     * @param attachments  图片/视频附件（可空，最多 6 个，单个 ≤50MB）
     */
    AppFeedbackIdVO submitFeedback(long userId, String content, String contactEmail,
                                   MultipartFile[] attachments) throws Exception;

    /**
     * 当前用户的反馈列表（未软删），按提交时间倒序。
     * <p>
     * 无分页（个人反馈量为百级，与投递记录一致）；附件 objectKey 已换成预签名 URL。
     */
    List<AppFeedbackListItemVO> listMyFeedbacks(long userId);
}
