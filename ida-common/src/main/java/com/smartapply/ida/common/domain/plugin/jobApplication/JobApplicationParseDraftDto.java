package com.smartapply.ida.common.domain.plugin.jobApplication;

import lombok.Data;

import java.util.List;

/**
 * 投递记录 LLM 提取草稿：{@code JobApplicationParseAssistant} 的反序列化目标，
 * 仅承载模型输出，不作 HTTP 响应（对外响应用 {@code PluginJobApplicationParseResultVO}）。
 */
@Data
public class JobApplicationParseDraftDto {

    /** 页面内识别到的全部投递记录；单岗位详情页通常只有 1 条 */
    private List<Record> records;

    @Data
    public static class Record {
        private String companyName;
        private String position;
        /** 进度 code（见 {@code JobApplicationProgress}），模型无法判断时为 null */
        private String progress;
        /** YYYY-MM-DD，页面无明确投递日期时为 null */
        private String applyDate;
    }
}
