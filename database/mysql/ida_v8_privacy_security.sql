-- =============================================================================
-- 智能投递助手 — v8 隐私安全基线（任务 1.4）
-- 内容：
--   1. ida_resume 敏感列扩长，以容纳 AES 密文（Base64，随机 IV，长度约为明文 1.5~2 倍）
--   2. ida_user 增加 deleted_at，支持账号注销（逻辑删除 + 匿名化）
-- 说明：
--   - 应用侧通过 MyBatis TypeHandler 对 full_name / id_doc_number / phone / email /
--     qq / wechat / detail_address / mailing_address / emergency_contact_name /
--     emergency_contact_phone 透明加解密（ida.crypto.aes-field.enabled=true）。
--   - 存量明文行：应用读取时"解密失败回退原值"可兼容；但 ida_user.phone 使用
--     确定性加密做等值登录查询，存量明文手机号将无法登录，开发期建议清库重建。
-- =============================================================================
USE `ida_db`;

-- -----------------------------------------------------------------------------
-- 1. ida_resume 敏感列扩长（phone/email/id_doc_number/地址/紧急联系人列长度已足够）
-- -----------------------------------------------------------------------------
ALTER TABLE `ida_resume`
    MODIFY COLUMN `full_name` VARCHAR(128) DEFAULT NULL COMMENT '姓名（AES 加密落库）',
    MODIFY COLUMN `qq`        VARCHAR(128) DEFAULT NULL COMMENT 'QQ（腾讯系网申常见，AES 加密落库）',
    MODIFY COLUMN `wechat`    VARCHAR(128) DEFAULT NULL COMMENT '微信号（AES 加密落库）';

-- -----------------------------------------------------------------------------
-- 2. ida_user 逻辑删除（账号注销）+ 手机号尾号盲索引（管理端密文之上的检索）
--    盲索引说明：phone 列为确定性 AES 密文，任意子串模糊匹配在数学上不可行；
--    业界通行做法（银行/运营商客服同构）为额外存后 4 位明文，支持"全号精确 /
--    后 4 位 / 用户 ID"三种查法。注册与登录时由应用懒回填。
-- -----------------------------------------------------------------------------
ALTER TABLE `ida_user`
    ADD COLUMN `deleted_at` DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间（账号注销）' AFTER `status`,
    ADD COLUMN `phone_tail` VARCHAR(8) DEFAULT NULL COMMENT '手机号后4位（盲索引，管理端尾号搜索）' AFTER `phone`,
    ADD KEY `idx_ida_user_phone_tail` (`phone_tail`);
