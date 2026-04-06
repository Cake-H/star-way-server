-- =============================================================================
-- 智能投递助手 — 核心数据模型 v1.1
-- 参考：腾讯招聘（join.qq 等）及国内头部互联网校招/社招简历常见模块，便于「一次填写 → 多站映射」。
-- 字符集：utf8mb4；引擎：InnoDB。
-- 安全：证件号、手机、邮箱、紧急联系人电话等列请在应用层加密后写入；禁止写明文进日志。
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `ida_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `ida_db`;
-- -----------------------------------------------------------------------------
-- 删除顺序：子表 → 简历 → 用户
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ida_site_fill_rule`;
DROP TABLE IF EXISTS `ida_resume_skill`;
DROP TABLE IF EXISTS `ida_resume_publication`;
DROP TABLE IF EXISTS `ida_resume_award`;
DROP TABLE IF EXISTS `ida_resume_certificate`;
DROP TABLE IF EXISTS `ida_resume_language`;
DROP TABLE IF EXISTS `ida_resume_campus`;
DROP TABLE IF EXISTS `ida_resume_project`;
DROP TABLE IF EXISTS `ida_resume_work`;
DROP TABLE IF EXISTS `ida_resume_education`;
DROP TABLE IF EXISTS `ida_resume`;
DROP TABLE IF EXISTS `ida_user`;

-- -----------------------------------------------------------------------------
-- 用户
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_user` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `phone`           VARCHAR(128) DEFAULT NULL COMMENT '登录手机号（建议加密）',
    `status`          TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ida_user_phone` (`phone`),
    KEY `idx_ida_user_status` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户主表';

-- -----------------------------------------------------------------------------
-- 简历主表：个人信息 + 求职意向 + 自我描述（与腾讯「基本信息/求职意向/自我评价」等大段对应）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`              BIGINT UNSIGNED NOT NULL,
    `title`                VARCHAR(128) NOT NULL DEFAULT '我的简历' COMMENT '简历名称，可「姓名-学校-岗位」',
    `is_default`           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '默认简历，业务层保证每用户至多一条为1',

    -- —— 基本信息（常见网申必填/选填）——
    `full_name`            VARCHAR(64) DEFAULT NULL COMMENT '姓名',
    `gender`               TINYINT UNSIGNED DEFAULT NULL COMMENT '0未知 1男 2女',
    `birth_date`           DATE DEFAULT NULL COMMENT '出生日期',
    `id_doc_type`          VARCHAR(32) DEFAULT NULL COMMENT '证件类型：身份证/护照等',
    `id_doc_number`        VARCHAR(256) DEFAULT NULL COMMENT '证件号码（强敏感，务必加密）',
    `phone`                VARCHAR(128) DEFAULT NULL COMMENT '手机（加密）',
    `email`                VARCHAR(256) DEFAULT NULL COMMENT '邮箱（加密）',
    `qq`                   VARCHAR(32) DEFAULT NULL COMMENT 'QQ（腾讯系网申常见）',
    `wechat`               VARCHAR(64) DEFAULT NULL COMMENT '微信号',
    `photo_url`            VARCHAR(512) DEFAULT NULL COMMENT '证件照/头像 URL',

    `country_region`       VARCHAR(64) DEFAULT NULL COMMENT '国家/地区',
    `native_place`         VARCHAR(128) DEFAULT NULL COMMENT '籍贯',
    `ethnicity`            VARCHAR(32) DEFAULT NULL COMMENT '民族',
    `political_status`     VARCHAR(32) DEFAULT NULL COMMENT '政治面貌',
    `hukou_type`           VARCHAR(32) DEFAULT NULL COMMENT '户口性质（城镇/农村等，按招聘方字典）',
    `province`             VARCHAR(64) DEFAULT NULL COMMENT '现居省/直辖市',
    `city`                 VARCHAR(64) DEFAULT NULL COMMENT '现居市',
    `district`             VARCHAR(64) DEFAULT NULL COMMENT '现居区/县',
    `current_city`         VARCHAR(128) DEFAULT NULL COMMENT '现居城市合并展示（冗余便于搜索/填充）',
    `detail_address`       VARCHAR(512) DEFAULT NULL COMMENT '详细通讯地址',
    `mailing_same_as_current` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '邮寄地址同现居',
    `mailing_address`      VARCHAR(512) DEFAULT NULL COMMENT '邮寄地址（部分校招表单独要）',

    `emergency_contact_name`  VARCHAR(64) DEFAULT NULL COMMENT '紧急联系人姓名',
    `emergency_contact_phone` VARCHAR(128) DEFAULT NULL COMMENT '紧急联系人电话（加密）',
    `emergency_contact_relation` VARCHAR(32) DEFAULT NULL COMMENT '与本人关系',

    -- —— 求职意向 ——
    `job_seeking_status`   TINYINT UNSIGNED DEFAULT NULL COMMENT '在职/离职/应届等，业务字典',
    `is_fresh_graduate`    TINYINT(1) DEFAULT NULL COMMENT '是否应届毕业生（校招关键字段）',
    `work_years`           VARCHAR(32) DEFAULT NULL COMMENT '工作年限描述，如「3年」社招常见',
    `expected_positions`   VARCHAR(1024) DEFAULT NULL COMMENT '期望岗位/职能，可 JSON 数组或分隔符',
    `expected_cities`      VARCHAR(1024) DEFAULT NULL COMMENT '期望工作城市，多选可 JSON',
    `expected_industries`  VARCHAR(512) DEFAULT NULL COMMENT '期望行业',
    `salary_expectation`   VARCHAR(128) DEFAULT NULL COMMENT '期望薪资',
    `availability`         VARCHAR(128) DEFAULT NULL COMMENT '可到岗时间',
    `accept_transfer`      TINYINT(1) DEFAULT NULL COMMENT '是否接受岗位/城市调剂',

    -- —— 自我评价 / 优势（腾讯常要求分点、可 STAR）——
    `self_intro`           MEDIUMTEXT COMMENT '自我介绍',
    `strengths`            MEDIUMTEXT COMMENT '个人优势/核心竞争力（可分点文本或 Markdown）',

    `portfolio_links`      JSON DEFAULT NULL COMMENT '作品集/个人主页链接列表 [{\"label\":\"\",\"url\":\"\"}]',
    `extra_json`           JSON DEFAULT NULL COMMENT '各平台差异字段兜底，供 AI 映射',
    `schema_version`       SMALLINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '简历结构版本',
    `row_version`          INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁',
    `deleted_at`           DATETIME(3) DEFAULT NULL,
    `created_at`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    KEY `idx_ida_resume_user` (`user_id`, `deleted_at`),
    KEY `idx_ida_resume_user_default` (`user_id`, `is_default`, `deleted_at`),
    CONSTRAINT `fk_ida_resume_user` FOREIGN KEY (`user_id`) REFERENCES `ida_user` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='简历主表';

-- -----------------------------------------------------------------------------
-- 教育经历（学校、学院、专业、全日制、GPA、主修课程、在校职务、荣誉等）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_education` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`          BIGINT UNSIGNED NOT NULL,
    `school_name`        VARCHAR(128) NOT NULL,
    `college_name`       VARCHAR(128) DEFAULT NULL COMMENT '学院/系',
    `major`              VARCHAR(128) DEFAULT NULL COMMENT '专业',
    `degree`             VARCHAR(32) DEFAULT NULL COMMENT '学历：本科/硕士等',
    `academic_degree`    VARCHAR(32) DEFAULT NULL COMMENT '学位：学士/硕士/博士',
    `is_full_time`       TINYINT(1) DEFAULT NULL COMMENT '是否全日制',
    `enroll_date`        DATE DEFAULT NULL,
    `graduate_date`      DATE DEFAULT NULL COMMENT '在读可空',
    `country_region`     VARCHAR(64) DEFAULT NULL COMMENT '就读国家/地区（留学）',
    `gpa`                VARCHAR(32) DEFAULT NULL COMMENT 'GPA 或均分',
    `ranking_desc`       VARCHAR(128) DEFAULT NULL COMMENT '成绩排名描述',
    `major_courses`      TEXT COMMENT '主修课程',
    `student_position`   VARCHAR(128) DEFAULT NULL COMMENT '在校职务，如班长',
    `honors`             TEXT COMMENT '在校获奖与荣誉（本段内简述）',
    `description`        TEXT COMMENT '其他说明',
    `sort_order`         INT NOT NULL DEFAULT 0,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_edu_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_edu_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='教育经历';

-- -----------------------------------------------------------------------------
-- 工作/实习经历（公司、部门、地点、性质、离职原因等）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_work` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`          BIGINT UNSIGNED NOT NULL,
    `company_name`       VARCHAR(128) NOT NULL,
    `department`         VARCHAR(128) DEFAULT NULL COMMENT '部门',
    `job_title`          VARCHAR(128) NOT NULL COMMENT '职位',
    `work_city`          VARCHAR(128) DEFAULT NULL COMMENT '工作城市',
    `company_industry`   VARCHAR(64) DEFAULT NULL COMMENT '公司行业',
    `employment_nature`  VARCHAR(32) DEFAULT NULL COMMENT '全职/实习/兼职等',
    `start_date`         DATE DEFAULT NULL,
    `end_date`           DATE DEFAULT NULL COMMENT '空表示至今',
    `work_description`   MEDIUMTEXT COMMENT '工作内容与成果（STAR）',
    `leave_reason`       VARCHAR(256) DEFAULT NULL COMMENT '离职原因（选填）',
    `sort_order`         INT NOT NULL DEFAULT 0,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_work_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_work_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='工作/实习经历';

-- -----------------------------------------------------------------------------
-- 项目经历（角色、技术栈、链接、成果）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_project` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`        BIGINT UNSIGNED NOT NULL,
    `project_name`     VARCHAR(256) NOT NULL,
    `role_name`        VARCHAR(128) DEFAULT NULL COMMENT '项目角色',
    `start_date`       DATE DEFAULT NULL,
    `end_date`         DATE DEFAULT NULL,
    `company_or_org`   VARCHAR(128) DEFAULT NULL COMMENT '所属公司/实验室（若与工作经历挂钩）',
    `tech_keywords`    VARCHAR(512) DEFAULT NULL COMMENT '技术栈/关键词',
    `project_url`      VARCHAR(512) DEFAULT NULL COMMENT '项目链接',
    `duty_description` MEDIUMTEXT COMMENT '本人职责',
    `achievement`      MEDIUMTEXT COMMENT '项目成果/数据',
    `description`      MEDIUMTEXT COMMENT '项目背景与描述',
    `sort_order`       INT NOT NULL DEFAULT 0,
    `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_proj_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_proj_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='项目经历';

-- -----------------------------------------------------------------------------
-- 校园经历/实践活动（社团、学生会、志愿活动，与工作表区分）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_campus` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`          BIGINT UNSIGNED NOT NULL,
    `organization_name`  VARCHAR(128) NOT NULL COMMENT '组织/活动名称',
    `role_title`         VARCHAR(128) DEFAULT NULL COMMENT '担任职务',
    `start_date`         DATE DEFAULT NULL,
    `end_date`           DATE DEFAULT NULL,
    `description`        MEDIUMTEXT,
    `sort_order`         INT NOT NULL DEFAULT 0,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_campus_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_campus_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='校园经历/实践';

-- -----------------------------------------------------------------------------
-- 专业技能（与岗位相关的硬技能+熟练度）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_skill` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`      BIGINT UNSIGNED NOT NULL,
    `skill_category` VARCHAR(64) DEFAULT NULL COMMENT '类别：编程语言/工具/软技能等',
    `skill_name`     VARCHAR(128) NOT NULL,
    `proficiency`    VARCHAR(64) DEFAULT NULL COMMENT '掌握程度描述',
    `months_of_use`  SMALLINT UNSIGNED DEFAULT NULL COMMENT '使用时长（月），选填',
    `sort_order`     INT NOT NULL DEFAULT 0,
    `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_skill_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_skill_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='专业技能';

-- -----------------------------------------------------------------------------
-- 语言能力（语种、等级、证书分数）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_language` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`     BIGINT UNSIGNED NOT NULL,
    `language_name` VARCHAR(64) NOT NULL COMMENT '如 英语、日语',
    `listen_level`  VARCHAR(32) DEFAULT NULL,
    `speak_level`   VARCHAR(32) DEFAULT NULL,
    `read_level`    VARCHAR(32) DEFAULT NULL,
    `write_level`   VARCHAR(32) DEFAULT NULL,
    `cert_name`     VARCHAR(128) DEFAULT NULL COMMENT '如 CET-6、雅思',
    `cert_score`    VARCHAR(64) DEFAULT NULL COMMENT '分数或等级',
    `sort_order`    INT NOT NULL DEFAULT 0,
    `created_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_lang_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_lang_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='语言能力';

-- -----------------------------------------------------------------------------
-- 证书/资格（与语言证书区分：职业资格证、PMP 等）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_certificate` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`    BIGINT UNSIGNED NOT NULL,
    `cert_name`    VARCHAR(256) NOT NULL,
    `issuer`       VARCHAR(128) DEFAULT NULL COMMENT '发证机构',
    `obtain_date`  DATE DEFAULT NULL,
    `cert_no`      VARCHAR(128) DEFAULT NULL COMMENT '证书编号（敏感可加密）',
    `sort_order`   INT NOT NULL DEFAULT 0,
    `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_cert_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_cert_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='证书与资格';

-- -----------------------------------------------------------------------------
-- 获奖情况（竞赛、奖学金等，可与教育经历 honors 互补）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_award` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`    BIGINT UNSIGNED NOT NULL,
    `award_name`   VARCHAR(256) NOT NULL COMMENT '奖项名称，如「国家奖学金」或「XX竞赛一等奖」',
    `award_level`  VARCHAR(64) DEFAULT NULL COMMENT '校级/省级/国家级等',
    `issuer`       VARCHAR(128) DEFAULT NULL COMMENT '颁奖机构',
    `award_date`   DATE DEFAULT NULL COMMENT '获奖日期',
    `description`  VARCHAR(512) DEFAULT NULL COMMENT '奖项描述或获奖作品简介',
    `sort_order`   INT NOT NULL DEFAULT 0,
    `created_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_award_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_award_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='获奖情况';

-- -----------------------------------------------------------------------------
-- 论文/专利/著作（研发岗常见，可选填）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_resume_publication` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `resume_id`      BIGINT UNSIGNED NOT NULL,
    `pub_type`       VARCHAR(32) NOT NULL COMMENT 'paper/patent/book/other',
    `title`          VARCHAR(512) NOT NULL,
    `role`           VARCHAR(64) DEFAULT NULL COMMENT '第一作者/发明人等',
    `venue`          VARCHAR(256) DEFAULT NULL COMMENT '期刊/会议/专利号',
    `publish_date`   DATE DEFAULT NULL,
    `description`    TEXT,
    `sort_order`     INT NOT NULL DEFAULT 0,
    `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_pub_resume` (`resume_id`, `sort_order`),
    CONSTRAINT `fk_pub_resume` FOREIGN KEY (`resume_id`) REFERENCES `ida_resume` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='论文/专利/著作';

-- -----------------------------------------------------------------------------
-- 站点填表映射（不变）
-- -----------------------------------------------------------------------------
CREATE TABLE `ida_site_fill_rule` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `site_host`          VARCHAR(255) NOT NULL,
    `path_pattern`       VARCHAR(512) NOT NULL DEFAULT '',
    `rule_name`          VARCHAR(128) NOT NULL DEFAULT 'default',
    `rule_json`          JSON NOT NULL,
    `created_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_site_rule` (`user_id`, `site_host`(191), `path_pattern`(191), `rule_name`(64)),
    KEY `idx_rule_user_host` (`user_id`, `site_host`),
    CONSTRAINT `fk_rule_user` FOREIGN KEY (`user_id`) REFERENCES `ida_user` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='站点字段映射规则';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 与「腾讯式」网申模块对应关系（便于产品与前端对齐）
-- 基本信息      → ida_resume（姓名、证件、联系方式、现居、籍贯、紧急联系人…）
-- 求职意向      → ida_resume（期望城市/岗位/行业、应届标识、工作年限、到岗、调剂…）
-- 教育经历      → ida_resume_education
-- 工作/实习     → ida_resume_work（含实习用 employment_nature）
-- 项目经历      → ida_resume_project
-- 校园/实践     → ida_resume_campus
-- 技能          → ida_resume_skill
-- 语言          → ida_resume_language
-- 证书          → ida_resume_certificate
-- 获奖          → ida_resume_award
-- 论文/专利     → ida_resume_publication
-- 自我评价/优势 → ida_resume.self_intro / strengths
-- 作品链接      → ida_resume.portfolio_links
-- 其它平台字段  → ida_resume.extra_json
-- =============================================================================
