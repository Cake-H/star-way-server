-- =============================================================================
-- 插件一键填表 反馈闭环 v1.3 — 增量脚本（在 ida_v1_init.sql / ida_v2_interview.sql 之后执行）
-- 表：ida_plugin_autofill_feedback（逐字段反馈明细）
-- 用途：消费 Redis 队列 ida:plugin:autofill:feedback:queue 入库，支撑字段级命中率统计，
--       驱动站点模板校准、同义词库扩充、提示词迭代。诊断数据，保留近 90 天。
-- =============================================================================
SET NAMES utf8mb4;

USE `ida_db`;

CREATE TABLE IF NOT EXISTS `ida_plugin_autofill_feedback` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT UNSIGNED NOT NULL,
    `resume_id`        BIGINT UNSIGNED NOT NULL,
    `task_id`          VARCHAR(64) NOT NULL DEFAULT '' COMMENT '异步任务 id，同步路径留空',
    `page_url`         VARCHAR(512) NOT NULL DEFAULT '',
    `site_host`        VARCHAR(191) NOT NULL DEFAULT '' COMMENT '从 pageUrl 解析的 host，按站点聚合',
    `site_id`          VARCHAR(64) NOT NULL DEFAULT '' COMMENT '命中的站点模板 id，前端从 plan 结果透传',
    `selector`         VARCHAR(512) NOT NULL DEFAULT '',
    `client_field_key` VARCHAR(128) NOT NULL DEFAULT '',
    `status`           VARCHAR(16) NOT NULL COMMENT 'filled / skipped / error',
    `fail_reason`      VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'option_not_found/readonly/date_nav_failed/selector_miss/other',
    `actual_value`     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '前端实际写入值，已脱敏掩码+截断',
    `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_feedback_site_field` (`site_host`, `client_field_key`, `status`),
    KEY `idx_feedback_user` (`user_id`, `created_at`),
    KEY `idx_feedback_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='插件一键填表逐字段反馈明细';
