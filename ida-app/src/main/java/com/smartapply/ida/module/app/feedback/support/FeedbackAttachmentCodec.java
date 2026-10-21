package com.smartapply.ida.module.app.feedback.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.feedback.dto.AppFeedbackAttachment;
import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackAttachmentVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.integration.oss.AliyunOssStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 反馈附件 JSON 编解码：写入时 {@code List<AppFeedbackAttachment>} → {@code attachments} 列 JSON；
 * 读取时把 objectKey 换成预签名 URL 再出参（Bucket 私有读，与简历原件一致只发短时效签名链接）。
 * <p>
 * 放在 support 包供 APP 端与管理端复用（ida-admin 依赖 ida-app）；
 * 管理端 OSS 未配置时 {@code url} 置空串，不影响反馈数据本身的查看。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackAttachmentCodec {

    /** 预签名 URL 有效期：对齐简历原件的 1 小时 */
    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final ObjectMapper objectMapper;
    /** 条件装配下可能不存在，故用 ObjectProvider 避免启动失败 */
    private final ObjectProvider<AliyunOssStorageService> ossStorageProvider;

    /** 附件数组 → attachments 列 JSON；无附件返回 null（列可空）。 */
    public String toJson(List<AppFeedbackAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException e) {
            // 简单 POJO 序列化不应失败；按业务异常抛出，避免落库半截数据
            throw new BusinessException("附件信息序列化失败");
        }
    }

    /** attachments 列 JSON → 对外展示结构（objectKey 换预签名 URL）；解析失败按无附件处理。 */
    public List<AppFeedbackAttachmentVO> toView(String attachmentsJson) {
        if (!StringUtils.hasText(attachmentsJson)) {
            return List.of();
        }
        List<AppFeedbackAttachment> stored;
        try {
            stored = objectMapper.readValue(attachmentsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("反馈附件 JSON 解析失败，按无附件返回: {}", e.getMessage());
            return List.of();
        }
        AliyunOssStorageService oss = ossStorageProvider.getIfAvailable();
        return stored.stream().map(a -> AppFeedbackAttachmentVO.builder()
                        .url(signUrl(oss, a.getObjectKey()))
                        .fileName(a.getFileName())
                        .mimeType(a.getMimeType())
                        .size(a.getSize())
                        .mediaType(a.getMediaType())
                        .build())
                .toList();
    }

    /** OSS 未启用或签名失败时返回空串，附件元信息仍正常出参 */
    private static String signUrl(AliyunOssStorageService oss, String objectKey) {
        if (oss == null) {
            return "";
        }
        String url = oss.generatePresignedUrl(objectKey, PRESIGN_TTL);
        return url != null ? url : "";
    }
}
