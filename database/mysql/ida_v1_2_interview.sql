-- =============================================================================
-- 面试复盘模块 v1.2 — 增量脚本（在 ida_v1_init.sql 之后执行）
-- 表：ida_interview_session（场次）、ida_interview_question（问题）
-- 软删：deleted_at 非空表示已删除；question_count 由应用层维护
-- =============================================================================
SET NAMES utf8mb4;

USE `ida_db`;

CREATE TABLE IF NOT EXISTS `ida_interview_session` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'sessionId',
    `user_id`         BIGINT UNSIGNED NOT NULL,
    `title`           VARCHAR(80) NOT NULL,
    `company_name`    VARCHAR(128) DEFAULT NULL,
    `position_name`   VARCHAR(128) DEFAULT NULL,
    `interview_round` VARCHAR(64) DEFAULT NULL,
    `interview_at`    DATE DEFAULT NULL,
    `question_count`  INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted_at`      DATETIME(3) DEFAULT NULL,
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_interview_session_user` (`user_id`, `deleted_at`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='面试复盘场次';

CREATE TABLE IF NOT EXISTS `ida_interview_question` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'questionId',
    `session_id`         BIGINT UNSIGNED NOT NULL,
    `sort_order`         INT NOT NULL DEFAULT 0,
    `question`           TEXT NOT NULL,
    `my_answer`          TEXT DEFAULT NULL,
    `answer_evaluation`  TEXT DEFAULT NULL,
    `reference_answer`   TEXT DEFAULT NULL,
    `other_notes`        TEXT DEFAULT NULL,
    `deleted_at`         DATETIME(3) DEFAULT NULL,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_interview_question_session` (`session_id`, `deleted_at`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='面试复盘问题';
