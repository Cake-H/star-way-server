package com.smartapply.ida.common.domain.interview.dto;

import lombok.Data;

/** JSON 方式调用 {@code voice-summarize} 时的请求体（无录音文件）。 */
@Data
public class InterviewVoiceSummarizeTextRequest {
    /** 浏览器 Web Speech 或用户粘贴的口述/转写全文 */
    private String rawTranscript;
}
