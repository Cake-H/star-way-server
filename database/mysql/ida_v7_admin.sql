-- =============================================================================
-- 智能投递助手 — 管理后台 v7：管理员账号 + 网申站点
-- 背景：M1.3 独立管理后台（ida-admin-server，端口 8090）。
--   1) ida_admin_account：后台管理员账号（账号密码体系，与 C 端 SMS 登录完全隔离）
--   2) ida_apply_site：网申站点目录（后台维护，C 端「网申网站列表」功能复用同一张表）
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- =============================================================================
USE `ida_db`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 管理员账号
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ida_admin_account`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员主键',
    `username`      VARCHAR(64)     NOT NULL COMMENT '登录用户名',
    `password_hash` VARCHAR(128)    NOT NULL COMMENT '密码 BCrypt 哈希（禁止存明文）',
    `name`          VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '显示名称',
    `role`          VARCHAR(32)     NOT NULL DEFAULT 'ADMIN' COMMENT '角色，当前仅 ADMIN，预留 RBAC 扩展',
    `status`        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    `last_login_at` DATETIME(3)     DEFAULT NULL COMMENT '最近登录时间',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_account_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='管理后台管理员账号';

-- 初始管理员：admin / Admin@123456（BCrypt 哈希）
-- !!! 首次登录后请立即通过数据库 UPDATE 更换密码哈希，测试环境之外禁止沿用默认口令 !!!
INSERT INTO `ida_admin_account` (`username`, `password_hash`, `name`, `role`)
SELECT 'admin', '$2b$10$pZW/SxnTGcXQat.qSdvRaukoYpbxrYznIswVmBU76y/YWQJs5pZea', '系统管理员', 'ADMIN'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `ida_admin_account` WHERE `username` = 'admin');

-- -----------------------------------------------------------------------------
-- 2. 网申站点目录（后台维护；C 端「网申网站列表」按 status=1 读取）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ida_apply_site`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '站点主键',
    `name`             VARCHAR(128)    NOT NULL COMMENT '站点名称，如「腾讯校招」',
    `logo_url`         VARCHAR(512)    DEFAULT NULL COMMENT '站点 Logo URL',
    `url`              VARCHAR(512)    NOT NULL COMMENT '网申入口 URL',
    `category`         VARCHAR(32)     NOT NULL DEFAULT '综合' COMMENT '分类：互联网/国企/银行/外企/快消/综合招聘/实习 等',
    `support_autofill` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否支持插件一键填表：1=支持 0=不支持',
    `sort_order`       INT             NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架',
    `remark`           VARCHAR(255)    DEFAULT NULL COMMENT '备注（内部说明）',
    `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_apply_site_category` (`category`, `status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='网申站点目录';

-- 种子数据：主流网申入口（仅当表为空时插入）
INSERT INTO `ida_apply_site` (`name`, `url`, `category`, `support_autofill`, `sort_order`, `remark`)
SELECT * FROM (
    SELECT '北森校招（Beisen）' AS name, 'https://www.beisen.com' AS url, 'ATS系统' AS category, 1 AS support_autofill, 1 AS sort_order, '已有插件站点模板' AS remark
    UNION ALL SELECT 'Moka 招聘', 'https://www.mokahr.com', 'ATS系统', 0, 2, 'M2 站点模板规划内'
    UNION ALL SELECT '智联招聘', 'https://www.zhaopin.com', '综合招聘', 0, 10, NULL
    UNION ALL SELECT '前程无忧', 'https://www.51job.com', '综合招聘', 0, 11, NULL
    UNION ALL SELECT 'BOSS直聘', 'https://www.zhipin.com', '综合招聘', 0, 12, NULL
    UNION ALL SELECT '实习僧', 'https://www.shixiseng.com', '实习', 0, 20, NULL
    UNION ALL SELECT '牛客网', 'https://www.nowcoder.com', '互联网', 0, 21, NULL
    UNION ALL SELECT '腾讯校招', 'https://join.qq.com', '互联网', 0, 30, NULL
    UNION ALL SELECT '阿里巴巴校招', 'https://campus.alibaba.com', '互联网', 0, 31, NULL
    UNION ALL SELECT '字节跳动校招', 'https://jobs.bytedance.com/campus', '互联网', 0, 32, NULL
) seed
WHERE NOT EXISTS (SELECT 1 FROM `ida_apply_site` LIMIT 1);
