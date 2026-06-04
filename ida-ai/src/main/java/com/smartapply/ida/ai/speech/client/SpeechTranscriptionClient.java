package com.smartapply.ida.ai.speech.client;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 录音听写：将上传音频转为纯文本，供面试总结等场景使用。
 */
public interface SpeechTranscriptionClient {

    /**
     * @return 听写正文；空串表示未识别出内容
     */
    String transcribe(MultipartFile file) throws IOException;
}
