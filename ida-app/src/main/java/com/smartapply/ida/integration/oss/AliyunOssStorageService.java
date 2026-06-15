package com.smartapply.ida.integration.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.smartapply.ida.config.props.oss.AliyunOssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 通用上传：按对象键写入 Bucket，可选返回公共访问 URL。
 * <p>
 * 业务侧按需调用 {@link #upload(MultipartFile, String)} / {@link #upload(InputStream, long, String, String)}；
 * 简历原件场景可使用 {@link #uploadResume(MultipartFile, long)}，内部按配置前缀与用户隔离路径。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(OSS.class)
public class AliyunOssStorageService {

    private final OSS oss;
    private final AliyunOssProperties properties;

    @jakarta.annotation.PostConstruct
    public void init() {
        System.out.println("[OSS-Service] AliyunOssStorageService Bean 已创建, endpoint=" + properties.getEndpoint() + " bucket=" + properties.getBucket());
    }

    /**
     * 通用上传：对象键由调用方指定（建议不含前导 {@code /}）。
     */
    public OssUploadResult upload(MultipartFile file, String objectKey) throws Exception {
        ObjectMetadata meta = new ObjectMetadata();
        if (file.getContentType() != null) {
            meta.setContentType(file.getContentType());
        }
        meta.setContentLength(file.getSize());
        try (InputStream in = file.getInputStream()) {
            oss.putObject(properties.getBucket(), objectKey, in, meta);
        }
        return toResult(objectKey);
    }

    /**
     * 通用上传：流由调用方在 {@link OSS#putObject} 返回后自行关闭（若需）。
     */
    public OssUploadResult upload(InputStream in, long contentLength, String contentType, String objectKey) {
        ObjectMetadata meta = new ObjectMetadata();
        if (StringUtils.hasText(contentType)) {
            meta.setContentType(contentType);
        }
        meta.setContentLength(contentLength);
        oss.putObject(properties.getBucket(), objectKey, in, meta);
        return toResult(objectKey);
    }

    /**
     * 简历原件：使用配置项 {@code ida.aliyun.oss.key-prefix}（默认 {@code app/resume/}）+ {@code userId/uuid+扩展名}。
     *
     * @param userId 当前登录用户，用于路径隔离
     */
    public OssUploadResult uploadResume(MultipartFile file, long userId) throws Exception {
        String objectKey = buildResumeObjectKey(file, userId);
        return upload(file, objectKey);
    }

    private String buildResumeObjectKey(MultipartFile file, long userId) {
        String ext = extensionOf(file.getOriginalFilename());
        String safePrefix = properties.getKeyPrefix() == null ? "app/resume/" : properties.getKeyPrefix();
        if (!safePrefix.endsWith("/")) {
            safePrefix = safePrefix + "/";
        }
        return safePrefix + userId + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String lower = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        return lower.length() <= 10 ? lower : "";
    }

    private OssUploadResult toResult(String objectKey) {
        return new OssUploadResult(properties.getBucket(), objectKey, resolvePublicUrl(objectKey));
    }

    private String resolvePublicUrl(String objectKey) {
        if (!StringUtils.hasText(properties.getPublicBaseUrl())) {
            return null;
        }
        String base = properties.getPublicBaseUrl().trim();
        if (base.endsWith("/")) {
            return base + objectKey;
        }
        return base + "/" + objectKey;
    }

    /**
     * @param bucket     Bucket 名
     * @param objectKey  OSS 对象键
     * @param publicUrl  若未配置公共基址则为 null
     */
    public record OssUploadResult(String bucket, String objectKey, String publicUrl) {}
}
