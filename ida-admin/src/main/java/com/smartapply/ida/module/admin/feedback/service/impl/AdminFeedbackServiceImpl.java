package com.smartapply.ida.module.admin.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.feedback.AppFeedback;
import com.smartapply.ida.common.domain.feedback.AppFeedbackStatus;
import com.smartapply.ida.common.domain.feedback.dto.AppFeedbackStatusUpdateRequest;
import com.smartapply.ida.common.domain.feedback.vo.AdminFeedbackVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.feedback.service.AdminFeedbackService;
import com.smartapply.ida.module.app.feedback.mapper.AppFeedbackMapper;
import com.smartapply.ida.module.app.feedback.support.FeedbackAttachmentCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理后台用户反馈服务实现。
 * <p>
 * 数据访问复用 APP 端 {@link AppFeedbackMapper}（ida-admin 依赖 ida-app，与后台用户管理一致）；
 * 附件出参经 {@link FeedbackAttachmentCodec} 把 objectKey 换成预签名 URL，OSS 未启用时 url 为空串。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    private final AppFeedbackMapper appFeedbackMapper;
    private final FeedbackAttachmentCodec attachmentCodec;

    @Override
    public PageVO<AdminFeedbackVO> pageFeedbacks(long page, long size, String status, String keyword) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<AppFeedback> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(AppFeedback::getStatus, requireStatusCode(status));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AppFeedback::getContent, kw)
                    .or().like(AppFeedback::getContactEmail, kw));
        }
        qw.orderByDesc(AppFeedback::getCreatedAt);

        Page<AppFeedback> result = appFeedbackMapper.selectPage(new Page<>(safePage, safeSize), qw);
        return PageVO.of(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AdminFeedbackVO getFeedbackDetail(Long feedbackId) {
        AppFeedback feedback = appFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        return toVO(feedback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFeedbackStatus(Long feedbackId, AppFeedbackStatusUpdateRequest req) {
        String statusCode = requireStatusCode(req.getStatus());
        LambdaUpdateWrapper<AppFeedback> uw = Wrappers.<AppFeedback>lambdaUpdate()
                .eq(AppFeedback::getId, feedbackId)
                .set(AppFeedback::getStatus, statusCode)
                .set(AppFeedback::getUpdatedAt, LocalDateTime.now());
        if (req.getAdminNote() != null) {
            uw.set(AppFeedback::getAdminNote, req.getAdminNote().trim());
        }
        int updated = appFeedbackMapper.update(null, uw);
        if (updated < 1) {
            throw new BusinessException("反馈不存在");
        }
        log.info("[后台] 更新反馈状态 feedbackId={} status={}", feedbackId, statusCode);
    }

    private AdminFeedbackVO toVO(AppFeedback f) {
        return AdminFeedbackVO.builder()
                .id(f.getId())
                .userId(f.getUserId())
                .source(f.getSource())
                .contactEmail(f.getContactEmail())
                .content(f.getContent())
                .attachments(attachmentCodec.toView(f.getAttachments()))
                .status(f.getStatus())
                .statusLabel(AppFeedbackStatus.labelOf(f.getStatus()))
                .adminNote(f.getAdminNote())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    /** 状态 code 合法性校验，合法则原样返回。 */
    private static String requireStatusCode(String status) {
        if (!StringUtils.hasText(status) || AppFeedbackStatus.fromCode(status.trim()) == null) {
            throw new BusinessException("反馈状态不合法");
        }
        return status.trim();
    }
}
