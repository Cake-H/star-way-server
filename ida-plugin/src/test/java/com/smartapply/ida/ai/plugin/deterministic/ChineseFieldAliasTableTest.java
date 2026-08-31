package com.smartapply.ida.ai.plugin.deterministic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 静态别名表单测：精确查找、最长包含查找（并列→null）、新增别名路径有效性。
 */
class ChineseFieldAliasTableTest {

    @Test
    void resolveByLabelContains_shouldMatchLongestContainedKey() {
        // 「您的姓名」包含静态 key「姓名」
        assertThat(ChineseFieldAliasTable.resolveByLabelContains("您的姓名"))
                .isEqualTo("basic.fullName");
    }

    @Test
    void resolveByLabelContains_tie_shouldReturnNull() {
        // 「电话」与「邮箱」同为长度 2 的并列最长 key → 歧义返回 null
        assertThat(ChineseFieldAliasTable.resolveByLabelContains("电话邮箱")).isNull();
    }

    @Test
    void resolveByLabelContains_noHit_shouldReturnNull() {
        assertThat(ChineseFieldAliasTable.resolveByLabelContains("验证码")).isNull();
        assertThat(ChineseFieldAliasTable.resolveByLabelContains("")).isNull();
        assertThat(ChineseFieldAliasTable.resolveByLabelContains(null)).isNull();
    }

    @Test
    void newAliases_shouldResolveToRegisteredPaths() {
        assertThat(ChineseFieldAliasTable.resolveByLabel("期望工作地点")).isEqualTo("job.expectedCities");
        assertThat(ChineseFieldAliasTable.resolveByLabel("可到岗时间")).isEqualTo("job.availability");
        assertThat(ChineseFieldAliasTable.resolveByLabel("何时到岗")).isEqualTo("job.availability");
        // 「入职时间」唯一映射到工作经历开始时间（重复 key 已修复，不再静默覆盖）
        assertThat(ChineseFieldAliasTable.resolveByLabel("入职时间")).isEqualTo("work.0.startDate");
        // 裸 key「工作地点」已删除（与「工作城市」语义冲突，错配代价高于漏配）
        assertThat(ChineseFieldAliasTable.resolveByLabel("工作地点")).isNull();
        assertThat(ChineseFieldAliasTable.resolveByLabel("当前职位")).isEqualTo("work.0.jobTitle");
        assertThat(ChineseFieldAliasTable.resolveByLabel("户口所在地")).isEqualTo("location.province");
        assertThat(ChineseFieldAliasTable.resolveByLabel("入学年份")).isEqualTo("education.0.enrollDate");
        assertThat(ChineseFieldAliasTable.resolveByLabel("毕业院校")).isEqualTo("education.0.schoolName");
        assertThat(ChineseFieldAliasTable.resolveByLabel("所学专业")).isEqualTo("education.0.major");
        assertThat(ChineseFieldAliasTable.resolveByLabel("工作单位")).isEqualTo("work.0.companyName");
        assertThat(ChineseFieldAliasTable.resolveByLabel("家庭住址")).isEqualTo("location.detailAddress");
        // 「学历类型」独立于「学历」：指向 isFullTime 而非 degree，禁止跨字段值域复用
        assertThat(ChineseFieldAliasTable.resolveByLabel("学历类型")).isEqualTo("education.0.isFullTime");
        assertThat(ChineseFieldAliasTable.resolveByLabel("是否全日制")).isEqualTo("education.0.isFullTime");
        assertThat(ChineseFieldAliasTable.resolveByLabel("当前居住城市")).isEqualTo("location.currentCity");
        // 所有新增/存量静态别名路径都必须在 ResumeFieldAccessor 注册
        assertThat(ResumeFieldAccessor.isKnownKey("job.expectedCities")).isTrue();
        assertThat(ResumeFieldAccessor.isKnownKey("work.0.jobTitle")).isTrue();
        assertThat(ResumeFieldAccessor.isKnownKey("education.0.enrollDate")).isTrue();
        assertThat(ResumeFieldAccessor.isKnownKey("education.0.isFullTime")).isTrue();
    }

    @Test
    void fullTimeEnumAlias_shouldMapCodeToDisplayText() {
        assertThat(ChineseFieldAliasTable.matchByEnumAlias("fullTime", "1",
                List.of("海外及港澳台", "统招全日制", "统招非全日制", "自考", "其他"))).isEqualTo("统招全日制");
        assertThat(ChineseFieldAliasTable.matchByEnumAlias("fullTime", "0",
                List.of("统招全日制", "统招非全日制"))).isEqualTo("统招非全日制");
        assertThat(ChineseFieldAliasTable.inferEnumCategory("education.0.isFullTime")).isEqualTo("fullTime");
    }

    @Test
    void matchByEnumAlias_gender_shouldNotMisContainsFemaleAsMale() {
        // 高危回归：性别 "1"（男）的别名组含 "male"，contains 误配时 "Female".contains("male") 命中
        // 两阶段修复后精确匹配层先命中 "Male"，不会把男填成女
        assertThat(ChineseFieldAliasTable.matchByEnumAlias("gender", "1", List.of("Female", "Male")))
                .isEqualTo("Male");
        assertThat(ChineseFieldAliasTable.matchByEnumAlias("gender", "2", List.of("Female", "Male")))
                .isEqualTo("Female");
    }

    @Test
    void matchByEnumAlias_containsScoringTie_shouldReturnNull() {
        // 第二阶段 contains 统一小写后打分竞争，并列最高且归一化后不同 → 歧义返回 null
        assertThat(ChineseFieldAliasTable.matchByEnumAlias("degree", "本科",
                List.of("本科(全日制)", "本科(专升本)"))).isNull();
        assertThat(ChineseFieldAliasTable.matchByEnumAlias("degree", "本科",
                List.of("大学本科", "硕士研究生"))).isEqualTo("大学本科");
    }
}
