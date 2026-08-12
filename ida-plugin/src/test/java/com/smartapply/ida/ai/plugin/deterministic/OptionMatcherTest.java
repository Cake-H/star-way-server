package com.smartapply.ida.ai.plugin.deterministic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下拉选项匹配器单测：归一化精确 → 枚举别名（提前）→ contains 打分竞争（并列→null）。
 */
class OptionMatcherTest {

    @Test
    void containsScoring_shouldPickBestCandidate() {
        // 只有「硕士研究生」与简历值存在包含关系，直接命中
        String hit = OptionMatcher.matchOption("硕士",
                List.of("硕士研究生", "博士研究生"), null);
        assertThat(hit).isEqualTo("硕士研究生");
    }

    @Test
    void containsScoring_shouldHandleEnglishSuffix() {
        String hit = OptionMatcher.matchOption("本科",
                List.of("专科", "大学本科(Bachelor)"), null);
        assertThat(hit).isEqualTo("大学本科(Bachelor)");
    }

    @Test
    void containsScoring_tie_shouldReturnNull() {
        // 两个候选同分（较短串/较长串相同）→ 歧义，降级交 LLM
        String hit = OptionMatcher.matchOption("本科",
                List.of("本科(全日制)", "本科(专升本)"), null);
        assertThat(hit).isNull();
    }

    @Test
    void enumAlias_shouldTakePriorityOverContains() {
        // 「研究生」与两个选项都是 contains 关系且同分；枚举别名层先命中「硕士」组，避免歧义返回 null
        String hit = OptionMatcher.matchOption("研究生",
                List.of("硕士研究生", "博士研究生"), "degree");
        assertThat(hit).isEqualTo("硕士研究生");
    }

    @Test
    void exactMatch_shouldWinBeforeEnumAlias() {
        String hit = OptionMatcher.matchOption("本科",
                List.of("本科", "大学本科"), "degree");
        assertThat(hit).isEqualTo("本科");
    }

    @Test
    void noMatch_shouldReturnNull() {
        assertThat(OptionMatcher.matchOption("博士", List.of("高中", "初中"), null)).isNull();
        assertThat(OptionMatcher.matchOption("  ", List.of("高中"), null)).isNull();
        assertThat(OptionMatcher.matchOption("本科", List.of(), null)).isNull();
    }
}
