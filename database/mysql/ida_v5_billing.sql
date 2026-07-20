-- =============================================================================
-- 智能投递助手 — 商业化底座 v5：AI 额度与用量计量
-- 背景：LLM 调用此前只设 maxTokens 上限、不记录用量，放量即亏损。
-- 本脚本为「额度守卫 + 用量计量 + 每日免费额度」提供数据支撑。
-- 幂等：可在已有库上重复执行（ALTER 用 INFORMATION_SCHEMA 守卫）。
-- =============================================================================
USE `ida_db`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. ida_user 扩展：额度余额 / 累计额度 / 会员等级（预留）/ 每日免费额度发放日
-- -----------------------------------------------------------------------------
-- balance: 当前可用额度点数（1 点 ≈ 配置换算的 token 数，见 ida.billing.tokens-per-point）
-- total_quota: 累计发放过的总额度（含每日免费、充值、兑换码），用于运营统计
-- member_level: 预留会员体系字段，M4 落地会员折扣/专属额度包时启用
-- last_daily_grant_date: 懒发放每日免费额度的去重依据（避免依赖定时任务，App/插件双端启动皆可触发）

DROP PROCEDURE IF EXISTS `ida_add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `ida_add_column_if_missing`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'ida_db' AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL `ida_add_column_if_missing`('ida_user', 'balance',
    '`balance` INT NOT NULL DEFAULT 0 COMMENT ''AI 额度余额（点）''');
CALL `ida_add_column_if_missing`('ida_user', 'total_quota',
    '`total_quota` INT NOT NULL DEFAULT 0 COMMENT ''累计发放额度（点）''');
CALL `ida_add_column_if_missing`('ida_user', 'member_level',
    '`member_level` VARCHAR(16) NOT NULL DEFAULT ''FREE'' COMMENT ''会员等级：FREE/PRO...（预留）''');
CALL `ida_add_column_if_missing`('ida_user', 'last_daily_grant_date',
    '`last_daily_grant_date` DATE DEFAULT NULL COMMENT ''最近一次每日免费额度发放日期''');

DROP PROCEDURE IF EXISTS `ida_add_column_if_missing`;

-- -----------------------------------------------------------------------------
-- 2. AI 用量记录：每次 LLM 调用一条，计费与运营分析的事实表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ida_ai_usage_record`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用量记录 ID',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '调用用户 ID',
    `biz_type`          VARCHAR(32)     NOT NULL COMMENT '业务类型：resume_parse/plugin_autofill/interview_summarize/mock_interview_generate',
    `biz_id`            BIGINT UNSIGNED DEFAULT NULL COMMENT '关联业务 ID（场次/简历等，可空）',
    `model`             VARCHAR(64)     DEFAULT NULL COMMENT '实际调用的模型 ID',
    `prompt_tokens`     INT             NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    `completion_tokens` INT             NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    `total_tokens`      INT             NOT NULL DEFAULT 0 COMMENT '总 token 数',
    `cost_points`       INT             NOT NULL DEFAULT 0 COMMENT '本次扣减额度点数',
    `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_usage_user_time` (`user_id`, `created_at`),
    KEY `idx_usage_biz_time` (`biz_type`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='AI 调用用量记录';

-- -----------------------------------------------------------------------------
-- 3. 额度流水：所有余额变动（每日发放/消耗/充值/兑换码/后台调整）的可审计流水
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ida_quota_transaction`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水 ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `type`          VARCHAR(16)     NOT NULL COMMENT '类型：daily_grant=每日免费/consume=消耗/recharge=充值/redeem=兑换码/admin_adjust=后台调整',
    `points`        INT             NOT NULL COMMENT '变动点数：增加为正，消耗为负',
    `balance_after` INT             NOT NULL COMMENT '变动后余额（冗余，便于对账）',
    `remark`        VARCHAR(255)    DEFAULT NULL COMMENT '备注（如消耗的业务类型、兑换码、操作人）',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_quota_tx_user_time` (`user_id`, `created_at`),
    KEY `idx_quota_tx_type_time` (`type`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='额度流水';
