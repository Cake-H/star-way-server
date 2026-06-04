package com.smartapply.ida.ai.speech.autoconfigure;

import com.smartapply.ida.ai.speech.config.SpeechToTextProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 始终注册听写配置属性，供面试复盘等模块读取 {@code min-transcript-chars} 等项；
 * 听写实现见 {@link AliyunSpeechAutoConfiguration} / {@link SpeechToTextAutoConfiguration}（由 {@code provider} 切换）。
 */
@AutoConfiguration
@EnableConfigurationProperties(SpeechToTextProperties.class)
public class SpeechToTextPropertiesAutoConfiguration {
}
