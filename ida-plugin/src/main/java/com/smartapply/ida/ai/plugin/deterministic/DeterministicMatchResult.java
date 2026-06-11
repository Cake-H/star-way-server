package com.smartapply.ida.ai.plugin.deterministic;

import com.smartapply.ida.common.domain.plugin.autofill.PluginBlockedFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性匹配的分区结果：已匹配字段（可直接生成 assignment）、未匹配字段（交给 LLM）、已识别的 blocked 字段。
 */
@Data
@Builder
@AllArgsConstructor
public class DeterministicMatchResult {

    /** 已确定匹配并生成值的字段——可直接作为 assignment 返回前端 */
    @Builder.Default
    private List<PluginFieldAssignmentDto> matchedAssignments = new ArrayList<>();

    /** 无法确定性匹配的字段——需交由 LLM 处理 */
    @Builder.Default
    private List<PluginPageFormFieldDto> unmatchedFields = new ArrayList<>();

    /** 确定性识别的 blocked 字段（如 currentValue 非空且 readonly） */
    @Builder.Default
    private List<PluginBlockedFieldDto> blockedFields = new ArrayList<>();

    /** 全部字段均未匹配的降级结果（用于异常兜底） */
    public static DeterministicMatchResult allUnmatched(List<PluginPageFormFieldDto> fields) {
        return DeterministicMatchResult.builder()
                .unmatchedFields(new ArrayList<>(fields))
                .build();
    }
}
