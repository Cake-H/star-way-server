-- 模拟面试主表
CREATE TABLE IF NOT EXISTS `ida_mock_interview_session`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模拟面试 ID',
    `user_id`             BIGINT       NOT NULL COMMENT '所属用户 ID',
    `resume_id`           BIGINT       NOT NULL COMMENT '关联简历 ID',
    `title`               VARCHAR(255) NOT NULL COMMENT '标题，如「Java 后端开发 · 模拟面试」',
    `status`              VARCHAR(32)  NOT NULL DEFAULT 'GENERATED' COMMENT '状态：GENERATED/PRACTICING/COMPLETED',
    `question_count`      INT          NOT NULL DEFAULT 0 COMMENT '题目数量',
    `current_question_id` BIGINT       NULL     DEFAULT NULL COMMENT '当前练习到的题目 ID',
    `answer_revealed`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '当前题目答案是否已展开：0/1',
    `deleted_at`          TIMESTAMP    NULL     DEFAULT NULL COMMENT '删除时间，软删',
    `created_at`          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mock_interview_session_user` (`user_id`,`deleted_at`, `updated_at` DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='模拟面试场次';

-- 模拟面试题目表
CREATE TABLE IF NOT EXISTS `ida_mock_interview_question`
(
    `id`                BIGINT    NOT NULL AUTO_INCREMENT COMMENT '题目 ID',
    `session_id` BIGINT    NOT NULL COMMENT '关联模拟面试 ID',
    `sort_order`        INT       NOT NULL COMMENT '题目顺序，从 1 开始',
    `question`          TEXT      NOT NULL COMMENT '问题内容',
    `reference_answer`  TEXT      NULL     DEFAULT NULL COMMENT '参考答案',
    `answer_evaluation` TEXT      NULL     DEFAULT NULL COMMENT '答题要点/评价维度',
    `other_notes`       TEXT      NULL     DEFAULT NULL COMMENT '补充说明',
    `deleted_at`        TIMESTAMP    NULL     DEFAULT NULL COMMENT '删除时间，软删',
    `created_at`        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mock_interview_question_session` (`session_id`, `deleted_at`, `sort_order`),
    CONSTRAINT `fk_question_mock_interview` FOREIGN KEY (`session_id`) REFERENCES `ida_mock_interview_session` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
COMMENT ='模拟面试题目';
