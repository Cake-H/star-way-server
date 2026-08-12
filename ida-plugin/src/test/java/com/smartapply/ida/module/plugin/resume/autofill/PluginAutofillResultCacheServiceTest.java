package com.smartapply.ida.module.plugin.resume.autofill;

import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 页面指纹单测：URL 归一化（query/fragment 不参与）与字段结构属性（placeholder/optionLabelsSample）参与哈希。
 */
class PluginAutofillResultCacheServiceTest {

    private static PluginPageFormFieldDto field() {
        PluginPageFormFieldDto f = new PluginPageFormFieldDto();
        f.setSelector("#name");
        f.setClientFieldKey("resume.full_name");
        f.setLabelText("姓名");
        f.setInteractionMode("plain_text");
        f.setPlaceholder("请输入姓名");
        f.setOptionLabelsSample(null);
        return f;
    }

    @Test
    void shouldProduceSameFingerprintWhenOnlyQueryDiffers() {
        List<PluginPageFormFieldDto> fields = List.of(field());
        String a = PluginAutofillResultCacheService.computePageFingerprint(
                "https://jobs.example.com/apply?jobId=111", fields);
        String b = PluginAutofillResultCacheService.computePageFingerprint(
                "https://jobs.example.com/apply?jobId=222&ref=banner#top", fields);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void shouldProduceDifferentFingerprintWhenOptionLabelsSampleChanges() {
        PluginPageFormFieldDto f1 = field();
        f1.setOptionLabelsSample(List.of("本科", "硕士"));
        PluginPageFormFieldDto f2 = field();
        f2.setOptionLabelsSample(List.of("本科", "硕士", "博士"));
        String a = PluginAutofillResultCacheService.computePageFingerprint(
                "https://jobs.example.com/apply?jobId=111", List.of(f1));
        String b = PluginAutofillResultCacheService.computePageFingerprint(
                "https://jobs.example.com/apply?jobId=111", List.of(f2));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldProduceDifferentFingerprintWhenPlaceholderChanges() {
        PluginPageFormFieldDto f1 = field();
        f1.setPlaceholder("请输入姓名");
        PluginPageFormFieldDto f2 = field();
        f2.setPlaceholder("Your name");
        String a = PluginAutofillResultCacheService.computePageFingerprint(
                "https://jobs.example.com/apply", List.of(f1));
        String b = PluginAutofillResultCacheService.computePageFingerprint(
                "https://jobs.example.com/apply", List.of(f2));
        assertThat(a).isNotEqualTo(b);
    }
}
