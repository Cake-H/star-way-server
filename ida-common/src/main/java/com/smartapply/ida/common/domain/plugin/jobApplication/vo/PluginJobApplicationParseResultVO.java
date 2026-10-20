package com.smartapply.ida.common.domain.plugin.jobApplication.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 插件「识别当前页面投递信息并入库」响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginJobApplicationParseResultVO {
    /** LLM 是否成功产出结构化结果；false = 已降级为仅存档网址+标题，待用户在 APP 补全 */
    private boolean parsed;
    /** 本次处理的全部记录（含新建与判重命中），顺序与页面中识别顺序一致 */
    private List<PluginJobApplicationParseItemVO> records;
}
