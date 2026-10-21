-- =============================================================================
-- APP 内用户反馈 — 增量脚本（在 ida_v11_job_appliaction.sql 之后执行）
-- 表：ida_app_feedback（APP/MAIL 渠道的用户反馈）
-- 说明：
--   - 附件不存库内二进制，仅记录 OSS 对象键数组 JSON：
--     [{objectKey,fileName,mimeType,size,mediaType}]，mediaType 为 IMAGE/VIDEO；
--   - 读取时由应用层将 objectKey 换成预签名 URL 返回给前端（Bucket 私有读）。
-- =============================================================================
SET NAMES utf8mb4;

USE `ida_db`;

CREATE TABLE IF NOT EXISTS `ida_app_feedback`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '提交用户ID',
    `source`        VARCHAR(16)     NOT NULL DEFAULT 'APP' COMMENT '来源: APP/MAIL',
    `contact_email` VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '联系邮箱',
    `content`       TEXT            NOT NULL COMMENT '反馈内容',
    `attachments`   JSON            NULL COMMENT '附件数组 [{objectKey,fileName,mimeType,size,mediaType}]',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING/PROCESSING/RESOLVED',
    `admin_note`    VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '处理备注',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at`    DATETIME(3)     NULL     DEFAULT NULL COMMENT '软删',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_created` (`status`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户反馈';
