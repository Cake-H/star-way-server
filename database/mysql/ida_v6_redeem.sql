-- =============================================================================
-- 智能投递助手 — 商业化底座 v6：兑换码充值（测试期过渡方案）
-- 背景：M1.2 目标是在不接入微信/支付宝支付通道的前提下，
--       让运营人员可以为种子用户发放 AI 额度。
-- =============================================================================
USE `ida_db`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 兑换码表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ida_redeem_code`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '兑换码记录 ID',
    `code`        VARCHAR(64)     NOT NULL COMMENT '兑换码（唯一，建议 12-24 位随机串）',
    `points`      INT             NOT NULL COMMENT '可兑换额度点数',
    `status`      VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE=可用/USED=已使用/EXPIRED=已过期/DISABLED=已禁用',
    `used_by`     BIGINT UNSIGNED DEFAULT NULL COMMENT '使用用户 ID',
    `used_at`     DATETIME(3)     DEFAULT NULL COMMENT '使用时间',
    `expire_time` DATETIME(3)     DEFAULT NULL COMMENT '过期时间，NULL 表示永不过期',
    `remark`      VARCHAR(255)    DEFAULT NULL COMMENT '生成备注（运营批次、用途等）',
    `created_by`  VARCHAR(64)     DEFAULT NULL COMMENT '生成人/系统标识',
    `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_redeem_code_code` (`code`),
    KEY `idx_redeem_code_status` (`status`, `expire_time`),
    KEY `idx_redeem_code_used_by` (`used_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='AI 额度兑换码';

-- -----------------------------------------------------------------------------
-- 示例：手动生成 10 张 100 点、30 天后过期的兑换码（测试期可直接执行）
-- -----------------------------------------------------------------------------
INSERT INTO `ida_redeem_code` (`code`, `points`, `expire_time`, `remark`, `created_by`)
SELECT UUID_SHORT(), 100, DATE_ADD(NOW(3), INTERVAL 30 DAY), 'seed-batch-001', 'admin'
FROM DUAL
UNION ALL SELECT UUID_SHORT(), 100, DATE_ADD(NOW(3), INTERVAL 30 DAY), 'seed-batch-001', 'admin';
