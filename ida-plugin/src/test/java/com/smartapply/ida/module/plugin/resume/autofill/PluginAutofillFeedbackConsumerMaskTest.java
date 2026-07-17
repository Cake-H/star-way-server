package com.smartapply.ida.module.plugin.resume.autofill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 反馈 actualValue 脱敏逻辑单测：手机号/身份证号掩码（前 3 后 4）+ 截断，
 * 重点验证身份证号不会被手机号正则误匹配、边界正确。
 */
class PluginAutofillFeedbackConsumerMaskTest {

    @Test
    void shouldMaskPhone() {
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("13800138000"))
                .isEqualTo("138****8000");
    }

    @Test
    void shouldMaskIdCard() {
        // 18 位：前 3 + 中 11 星 + 后 4
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("110101199001011234"))
                .isEqualTo("110***********1234");
    }

    @Test
    void shouldMaskIdCardEndingWithX() {
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("11010119900101123X"))
                .isEqualTo("110***********123X");
    }

    @Test
    void shouldNotMisinterpretIdCardAsPhone() {
        // 18 位身份证含 11 位子串，但手机号正则有边界，身份证应整体被身份证正则掩码
        String result = PluginAutofillFeedbackConsumer.maskAndTruncate("110101199001011234");
        assertThat(result).doesNotContain("138"); // 不会出现手机号式掩码残留
        assertThat(result).isEqualTo("110***********1234");
    }

    @Test
    void shouldLeaveNormalTextUnmasked() {
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("张三"))
                .isEqualTo("张三");
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("清华大学计算机系"))
                .isEqualTo("清华大学计算机系");
    }

    @Test
    void shouldTruncateLongText() {
        String longText = "a".repeat(100);
        String result = PluginAutofillFeedbackConsumer.maskAndTruncate(longText);
        assertThat(result).hasSize(64);
    }

    @Test
    void shouldHandleNullAndBlank() {
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate(null)).isEmpty();
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("")).isEmpty();
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("   ")).isEmpty();
    }

    @Test
    void shouldMaskPhoneEmbeddedInText() {
        // 手机号前后非数字边界即可被掩码
        assertThat(PluginAutofillFeedbackConsumer.maskAndTruncate("手机是13800138000对吧"))
                .isEqualTo("手机是138****8000对吧");
    }
}
