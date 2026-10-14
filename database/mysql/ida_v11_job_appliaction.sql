CREATE TABLE IF NOT EXISTS `ida_job_application`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `company_name` VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '投递公司',
    `position`     VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '投递岗位',
    `apply_date`   DATE            NULL COMMENT '投递日期',
    `progress`     VARCHAR(32)     NOT NULL DEFAULT 'APPLIED' COMMENT '投递进度',
    `apply_url`    VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '网申地址',
    `source`       VARCHAR(16)     NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL/PLUGIN',
    `remark`       VARCHAR(1024)   NOT NULL DEFAULT '' COMMENT '备注',
    `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at`   DATETIME(3)     NULL     DEFAULT NULL COMMENT '软删',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `apply_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='投递记录';