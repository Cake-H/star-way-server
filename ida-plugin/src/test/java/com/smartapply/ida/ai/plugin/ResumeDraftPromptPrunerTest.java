package com.smartapply.ida.ai.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PART_A 简历 JSON 相关性裁剪器单测：验证无关模块剔除、basic/location 始终保留、
 * textarea/compound/clientFieldKey 推断、无法推断时全量保留、异常输入静默降级。
 */
class ResumeDraftPromptPrunerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeDraftPromptPruner pruner = new ResumeDraftPromptPruner(objectMapper);

    /** 含 basic/location/profile/job 标量 + 多个子表模块的 draft JSON */
    private static final String DRAFT_JSON = """
            {"fullName":"张三","phone":"13800001111","city":"北京",
             "selfIntro":"热爱后端开发","expectedPositions":"Java工程师",
             "educations":[{"schoolName":"清华大学","major":"计算机"}],
             "works":[{"companyName":"字节跳动","workDescription":"负责订单系统"}],
             "projects":[{"projectName":"秒杀系统","dutyDescription":"负责库存扣减"}],
             "skills":[{"skillName":"Java"}],
             "certificates":[{"certName":"CET-6"}]}
            """;

    @Test
    void shouldRemoveIrrelevantModulesAndKeepBasicLocation() throws Exception {
        PluginPageFormFieldDto school = new PluginPageFormFieldDto();
        school.setSelector("#school");
        school.setTagName("input");
        school.setLabelText("学校名称：");

        String pruned = pruner.prune(DRAFT_JSON, List.of(school));
        JsonNode node = objectMapper.readTree(pruned);

        // basic / location 标量始终保留
        assertThat(node.has("fullName")).isTrue();
        assertThat(node.has("phone")).isTrue();
        assertThat(node.has("city")).isTrue();
        // 命中的 education 模块保留
        assertThat(node.has("educations")).isTrue();
        // 无关模块被剔除
        assertThat(node.has("works")).isFalse();
        assertThat(node.has("projects")).isFalse();
        assertThat(node.has("skills")).isFalse();
        assertThat(node.has("certificates")).isFalse();
        assertThat(node.has("selfIntro")).isFalse();
        assertThat(node.has("expectedPositions")).isFalse();
    }

    @Test
    void shouldInferModuleFromClientFieldKey() throws Exception {
        PluginPageFormFieldDto company = new PluginPageFormFieldDto();
        company.setSelector("#company");
        company.setTagName("input");
        company.setClientFieldKey("work.0.companyName");

        String pruned = pruner.prune(DRAFT_JSON, List.of(company));
        JsonNode node = objectMapper.readTree(pruned);

        assertThat(node.has("works")).isTrue();
        assertThat(node.has("educations")).isFalse();
        assertThat(node.has("fullName")).isTrue();
    }

    @Test
    void shouldKeepProfileWorkProjectForTextarea() throws Exception {
        PluginPageFormFieldDto textarea = new PluginPageFormFieldDto();
        textarea.setSelector("#desc");
        textarea.setTagName("textarea");
        textarea.setInteractionMode("textarea");

        String pruned = pruner.prune(DRAFT_JSON, List.of(textarea));
        JsonNode node = objectMapper.readTree(pruned);

        assertThat(node.has("selfIntro")).isTrue();
        assertThat(node.has("works")).isTrue();
        assertThat(node.has("projects")).isTrue();
        assertThat(node.has("educations")).isFalse();
        assertThat(node.has("skills")).isFalse();
    }

    @Test
    void shouldKeepDateRangeModulesForDateCompoundSegment() throws Exception {
        PluginPageFormFieldDto dateStart = new PluginPageFormFieldDto();
        dateStart.setSelector("#start");
        dateStart.setTagName("input");
        dateStart.setCompoundSegment("date_start");

        String pruned = pruner.prune(DRAFT_JSON, List.of(dateStart));
        JsonNode node = objectMapper.readTree(pruned);

        assertThat(node.has("educations")).isTrue();
        assertThat(node.has("works")).isTrue();
        assertThat(node.has("projects")).isTrue();
        assertThat(node.has("skills")).isFalse();
        assertThat(node.has("certificates")).isFalse();
    }

    @Test
    void shouldKeepAllModulesWhenFieldCannotBeInferred() {
        PluginPageFormFieldDto unknown = new PluginPageFormFieldDto();
        unknown.setSelector("#mystery");
        unknown.setTagName("input");
        unknown.setLabelText("神秘自定义字段xyz");

        String pruned = pruner.prune(DRAFT_JSON, List.of(unknown));

        assertThat(pruned).isEqualTo(DRAFT_JSON);
    }

    @Test
    void shouldReturnOriginalOnInvalidJson() {
        String invalid = "not-a-json{";

        assertThat(pruner.prune(invalid, List.of(new PluginPageFormFieldDto()))).isEqualTo(invalid);
    }

    @Test
    void shouldReturnOriginalWhenUnmatchedFieldsEmpty() {
        assertThat(pruner.prune(DRAFT_JSON, List.of())).isEqualTo(DRAFT_JSON);
        assertThat(pruner.prune(DRAFT_JSON, null)).isEqualTo(DRAFT_JSON);
    }

    @Test
    void shouldReduceLengthSignificantly() {
        PluginPageFormFieldDto school = new PluginPageFormFieldDto();
        school.setSelector("#school");
        school.setTagName("input");
        school.setLabelText("学校");

        String pruned = pruner.prune(DRAFT_JSON, List.of(school));

        System.out.printf("PART_A 裁剪示例: 裁剪前=%d字符 裁剪后=%d字符%n", DRAFT_JSON.length(), pruned.length());
        assertThat(pruned.length()).isLessThan(DRAFT_JSON.length());
    }
}
