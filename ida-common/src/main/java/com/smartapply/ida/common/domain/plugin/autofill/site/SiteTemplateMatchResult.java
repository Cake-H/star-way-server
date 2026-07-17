package com.smartapply.ida.common.domain.plugin.autofill.site;

import com.smartapply.ida.common.domain.plugin.autofill.PluginBlockedFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 站点模板匹配结果：与 {@code DeterministicMatchResult} 结构对称，便于在 {@code PluginFormAutofillAiService} 中串联。
 * <p>
 * - {@code matchedAssignments}：模板金标命中并成功取值/匹配选项的字段，直接生成 assignment。
 * - {@code blockedFields}：模板标注 skip=true 的字段，直接进 blocked。
 * - {@code unmatchedFields}：未命中模板、或命中但下拉选项匹配失败、或为复合/长文本的字段，流转到下一层（确定性匹配/LLM）。
 * - {@code siteId}：命中的模板 id（未命中为 null），用于日志与反馈记录。
 */
@Data
@Builder
public class SiteTemplateMatchResult {

    private String siteId;

    private List<PluginFieldAssignmentDto> matchedAssignments;
    private List<PluginBlockedFieldDto> blockedFields;
    private List<PluginPageFormFieldDto> unmatchedFields;

    /** 未命中任何模板时的空结果便捷构造 */
    public static SiteTemplateMatchResult empty(List<PluginPageFormFieldDto> allFields) {
        return SiteTemplateMatchResult.builder()
                .siteId(null)
                .matchedAssignments(List.of())
                .blockedFields(List.of())
                .unmatchedFields(allFields)
                .build();
    }
}
