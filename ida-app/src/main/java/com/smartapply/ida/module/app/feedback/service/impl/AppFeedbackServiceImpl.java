package com.smartapply.ida.module.app.feedback.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.feedback.AppFeedback;
import com.smartapply.ida.common.domain.feedback.AppFeedbackMediaType;
import com.smartapply.ida.common.domain.feedback.AppFeedbackStatus;
import com.smartapply.ida.common.domain.feedback.dto.AppFeedbackAttachment;
import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackIdVO;
import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackListItemVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.integration.oss.AliyunOssStorageService;
import com.smartapply.ida.module.app.feedback.mapper.AppFeedbackMapper;
import com.smartapply.ida.module.app.feedback.service.AppFeedbackService;
import com.smartapply.ida.module.app.feedback.support.FeedbackAttachmentCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * APP 内用户反馈落库。
 * <p>
 * <b>数据策略：</b>通过 {@link com.baomidou.mybatisplus.annotation.TableLogic} 软删；APP 端提交的来源固定为
 * {@link #SOURCE_APP}，初始状态 {@link AppFeedbackStatus#PENDING}，后续由管理端流转。
 * <b>附件策略：</b>先传 OSS（按用户隔离路径）再落库；落库失败时已上传对象暂无 delete 能力可清理，
 * 记录 warn 日志等待人工/OSS 生命周期处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppFeedbackServiceImpl implements AppFeedbackService {

    /** 反馈来源：APP 端提交 */
    private static final String SOURCE_APP = "APP";

    /** 附件数量上限 */
    private static final int MAX_ATTACHMENTS = 6;
    /** 单个附件大小上限：50MB */
    private static final long MAX_ATTACHMENT_SIZE = 50L * 1024 * 1024;
    /** 附件 MIME 白名单：常见图片 + MP4/MOV 视频 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/quicktime");
    /** OSS 对象键前缀：app/feedback/{userId}/{uuid}{扩展名} */
    private static final String KEY_PREFIX = "app/feedback/";

    private final AppFeedbackMapper appFeedbackMapper;
    /** 条件装配下可能不存在，故用 ObjectProvider 避免启动失败 */
    private final ObjectProvider<AliyunOssStorageService> ossStorageProvider;
    private final FeedbackAttachmentCodec attachmentCodec;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppFeedbackIdVO submitFeedback(long userId, String content, String contactEmail,
                                          MultipartFile[] attachments) throws Exception {
        List<MultipartFile> files = attachments == null ? List.of()
                : Arrays.stream(attachments).filter(f -> f != null && !f.isEmpty()).toList();
        validateAttachments(files);

        // 步骤 1：附件先传 OSS（与简历原件同一服务），失败直接中断不落库
        List<AppFeedbackAttachment> uploaded = new ArrayList<>();
        if (!files.isEmpty()) {
            AliyunOssStorageService oss = ossStorageProvider.getIfAvailable();
            if (oss == null) {
                throw new BusinessException("未启用阿里云 OSS：请在配置中设置 ida.aliyun.oss.enabled=true 并填写 endpoint、bucket、AK/SK");
            }
            for (MultipartFile f : files) {
                String objectKey = buildObjectKey(f, userId);
                oss.upload(f, objectKey);
                uploaded.add(AppFeedbackAttachment.builder()
                        .objectKey(objectKey)
                        .fileName(f.getOriginalFilename())
                        .mimeType(f.getContentType())
                        .size(f.getSize())
                        .mediaType(mediaTypeOf(f.getContentType()))
                        .build());
            }
        }

        // 步骤 2：落库；失败时已上传的 OSS 对象记录对象键等待清理（OSS 服务暂无 delete 方法）
        LocalDateTime now = LocalDateTime.now();
        AppFeedback feedback = new AppFeedback();
        feedback.setUserId(userId);
        feedback.setSource(SOURCE_APP);
        feedback.setContactEmail(contactEmail != null ? contactEmail.trim() : "");
        feedback.setContent(content.trim());
        feedback.setAttachments(attachmentCodec.toJson(uploaded));
        feedback.setStatus(AppFeedbackStatus.PENDING.getCode());
        feedback.setAdminNote("");
        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);
        try {
            appFeedbackMapper.insert(feedback);
        } catch (Exception e) {
            log.warn("反馈落库失败，已上传的 OSS 附件待清理 userId={} objectKeys={}",
                    userId, uploaded.stream().map(AppFeedbackAttachment::getObjectKey).toList(), e);
            throw e;
        }
        if (feedback.getId() == null) {
            throw new BusinessException("提交反馈失败");
        }
        log.info("用户反馈提交成功 userId={} feedbackId={} attachmentCount={}", userId, feedback.getId(), uploaded.size());
        return AppFeedbackIdVO.builder().feedbackId(feedback.getId()).build();
    }

    @Override
    public List<AppFeedbackListItemVO> listMyFeedbacks(long userId) {
        List<AppFeedback> rows = appFeedbackMapper.selectList(Wrappers.<AppFeedback>lambdaQuery()
                .eq(AppFeedback::getUserId, userId)
                .orderByDesc(AppFeedback::getCreatedAt));
        return rows.stream().map(this::toListItem).toList();
    }

    private AppFeedbackListItemVO toListItem(AppFeedback f) {
        return AppFeedbackListItemVO.builder()
                .feedbackId(f.getId())
                .content(f.getContent())
                .attachments(attachmentCodec.toView(f.getAttachments()))
                .status(f.getStatus())
                .statusLabel(AppFeedbackStatus.labelOf(f.getStatus()))
                .adminNote(f.getAdminNote())
                .createdAt(f.getCreatedAt())
                .build();
    }

    /** 附件数量/大小/MIME 白名单校验 */
    private static void validateAttachments(List<MultipartFile> files) {
        if (files.size() > MAX_ATTACHMENTS) {
            throw new BusinessException("附件最多上传 " + MAX_ATTACHMENTS + " 个");
        }
        for (MultipartFile f : files) {
            if (f.getSize() > MAX_ATTACHMENT_SIZE) {
                throw new BusinessException("单个附件不能超过 50MB");
            }
            String contentType = f.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessException("附件仅支持图片（jpg/png/webp/gif）或视频（mp4/mov）");
            }
        }
    }

    /** 对象键：app/feedback/{userId}/{UUID无横线}{小写扩展名} */
    private static String buildObjectKey(MultipartFile file, long userId) {
        return KEY_PREFIX + userId + "/" + UUID.randomUUID().toString().replace("-", "") + extensionOf(file.getOriginalFilename());
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String lower = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        return lower.length() <= 10 ? lower : "";
    }

    private static String mediaTypeOf(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("video/")
                ? AppFeedbackMediaType.VIDEO.name()
                : AppFeedbackMediaType.IMAGE.name();
    }
}
