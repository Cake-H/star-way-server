-- =============================================================================
-- 插件一键填表 M2：动态字段别名表 — 增量脚本（在 ida_v3_plugin_feedback.sql 之后执行）
-- 表：ida_plugin_autofill_alias（标签别名 → 简历字段路径）
-- 用途：反馈闭环中 admin 根据 alias-candidates 审核登记的别名，status=approved 后由
--       DynamicFieldAliasService 定时全量加载，参与确定性标签匹配（优先级低于静态表）。
-- =============================================================================
SET NAMES utf8mb4;

USE `ida_db`;

CREATE TABLE IF NOT EXISTS `ida_plugin_autofill_alias` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `alias_label` VARCHAR(128) NOT NULL COMMENT '归一化后的表单标签（如「个人邮箱」），匹配时与前端 labelText 归一化结果比对',
    `field_path`  VARCHAR(128) NOT NULL COMMENT '简历字段路径，格式同 ResumeFieldAccessor（如 basic.email、education.0.schoolName）',
    `source`      VARCHAR(16) NOT NULL DEFAULT 'manual' COMMENT '来源：feedback（反馈挖掘）/ manual（人工登记）',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending / approved / rejected；仅 approved 参与匹配',
    `remark`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注（来源站点、样例页面等）',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alias_label` (`alias_label`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='插件一键填表动态字段别名（admin 审核后生效）';
