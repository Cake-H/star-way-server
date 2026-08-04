package com.smartapply.ida.config.props.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS：通用文件存储（上传由 {@link com.smartapply.ida.integration.oss.AliyunOssStorageService} 完成）。
 * <p>
 * <b>开通与配置步骤（摘要）：</b>
 * <ol>
 *   <li>阿里云控制台开通 OSS，创建 Bucket（建议私有读 + 服务端签名 URL 下载）。</li>
 *   <li>RAM 用户授予该 Bucket 的 {@code oss:PutObject}、{@code oss:GetObject}（若需临时链接再开）。</li>
 *   <li>将 AccessKey 配到环境变量或下方占位符，勿提交仓库。</li>
 *   <li>{@code public-base-url}：仅当 Bucket 公共读或绑定了自定义 CDN 域名时填写，用于直接返回浏览器可访问链接；私有桶可留空。</li>
 * </ol>
 */
@Data
@ConfigurationProperties(prefix = "ida.aliyun.oss")
public class AliyunOssProperties {

    /**
     * 是否启用 OSS；false 时上传接口将直接失败并提示配置（避免误把生产文件落到本地盘）。
     */
    private boolean enabled = false;

    /** 如 https://oss-cn-hangzhou.aliyuncs.com */
    private String endpoint = "";

    private String accessKeyId = "";

    private String accessKeySecret = "";

    /** Bucket 名称（全局唯一） */
    private String bucket = "";

    /**
     * 简历上传 {@link com.smartapply.ida.integration.oss.AliyunOssStorageService#uploadResume} 使用的对象键前缀，
     * 如 {@code app/resume/}，最终路径为 {@code prefix + userId + "/" + uuid + ext}。
     */
    private String keyPrefix = "app/resume/";

    /**
     * 可选：公共访问基址，例如 {@code https://your-bucket.oss-cn-hangzhou.aliyuncs.com} 或自定义域名（无尾斜杠）。
     *
     * @deprecated 隐私安全基线（1.4）起，简历原件一律走私有桶 +
     * {@link com.smartapply.ida.integration.oss.AliyunOssStorageService#generatePresignedUrl(String, java.time.Duration)}
     * 预签名 URL，不再向前端返回公开直链；本配置仅为兼容保留，请勿填写。
     */
    @Deprecated
    private String publicBaseUrl = "";
}
