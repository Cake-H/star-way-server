package com.smartapply.ida.ai.speech.client;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.smartapply.ida.ai.speech.config.SpeechToTextProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云智能语音交互 — 实时语音识别：将上传录音分片 {@code send} 至服务端（模拟实时流）。
 * <p>
 * SDK 仅支持 {@link InputFormatEnum}：PCM / WAV / OPUS / OPU / SPEEX；浏览器 webm 多为 OPUS，wav/pcm 最稳。
 * 官方文档：https://help.aliyun.com/zh/isi/developer-reference/sdk-for-java-8
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunNlsRealtimeTranscriptionClient implements SpeechTranscriptionClient {

    private final SpeechToTextProperties properties;

    @Override
    public String transcribe(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件为空");
        }
        SpeechToTextProperties.Aliyun cfg = properties.getAliyun();
        if (!StringUtils.hasText(cfg.getAppKey())) {
            throw new IllegalStateException("未配置 ida.speech-to-text.aliyun.app-key");
        }
        String accessKeyId = cfg.getAccessKeyId();
        String accessKeySecret = cfg.getAccessKeySecret();
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new IllegalStateException(
                    "未配置阿里云 AccessKey，请设置 ida.speech-to-text.aliyun.access-key-id / access-key-secret"
                            + "（或环境变量 ALIBABA_ACCESS_KEY_ID、ALIBABA_ACCESS_KEY_SECRET）");
        }

        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        try {
            accessToken.apply();
        } catch (IOException e) {
            throw new IllegalStateException("获取阿里云 NLS Token 失败: " + e.getMessage(), e);
        }

        String token = accessToken.getToken();
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("阿里云 NLS Token 为空");
        }

        int sampleRate = cfg.getSampleRate() == 8000 ? 8000 : 16000;
        NlsClient nlsClient = new NlsClient(cfg.resolveGatewayUrl(), token);
        SpeechTranscriber transcriber = null;
        try {
            CollectingListener listener = new CollectingListener();
            transcriber = new SpeechTranscriber(nlsClient, listener);
            transcriber.setAppKey(cfg.getAppKey().trim());
            transcriber.setFormat(resolveFormat(file, cfg));
            transcriber.setSampleRate(resolveSampleRate(sampleRate));
            transcriber.setEnableIntermediateResult(false);
            transcriber.setEnablePunctuation(true);
            transcriber.start();

            int chunkSize = Math.max(1600, cfg.getSendChunkBytes());
            byte[] buffer = new byte[chunkSize];
            try (InputStream in = file.getInputStream()) {
                int len;
                while ((len = in.read(buffer)) > 0) {
                    transcriber.send(buffer, len);
                    // 模拟实时发送，避免 GET_CLIENT_DATA_TIMEOUT
                    int sleepMs = sleepDeltaMs(len, sampleRate);
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("阿里云实时听写被中断", e);
            }
            transcriber.stop();

            boolean finished = listener.await(cfg.getReadTimeoutMs());
            if (!finished) {
                throw new IllegalStateException("阿里云实时听写超时（" + cfg.getReadTimeoutMs() + "ms）");
            }
            if (listener.failureMessage != null) {
                throw new IllegalStateException("阿里云实时听写失败: " + listener.failureMessage);
            }
            return listener.transcript.toString().trim();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("阿里云实时听写异常: " + e.getMessage(), e);
        } finally {
            if (transcriber != null) {
                transcriber.close();
            }
            nlsClient.shutdown();
        }
    }

    /** 与官方 SpeechTranscriberDemo#getSleepDelta 一致 */
    private static int sleepDeltaMs(int dataSize, int sampleRate) {
        int sampleBytes = 16;
        int soundChannel = 1;
        return (dataSize * 10 * 8000) / (160 * sampleRate * sampleBytes * soundChannel);
    }

    private static InputFormatEnum resolveFormat(MultipartFile file, SpeechToTextProperties.Aliyun cfg) {
        if (StringUtils.hasText(cfg.getDefaultFormat())) {
            return parseFormat(cfg.getDefaultFormat().trim());
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("wav")) {
                return InputFormatEnum.WAV;
            }
            if (ct.contains("webm") || ct.contains("ogg") || ct.contains("opus")) {
                return InputFormatEnum.OPUS;
            }
            if (ct.contains("pcm")) {
                return InputFormatEnum.PCM;
            }
            if (ct.contains("mpeg") || ct.contains("mp3")) {
                throw new IllegalArgumentException(
                        "实时识别 SDK 不支持 MP3 枚举，请上传 wav/pcm 或配置 ida.speech-to-text.aliyun.default-format=opus（webm）");
            }
        }
        String name = file.getOriginalFilename();
        if (StringUtils.hasText(name)) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".wav")) {
                return InputFormatEnum.WAV;
            }
            if (lower.endsWith(".webm") || lower.endsWith(".ogg")) {
                return InputFormatEnum.OPUS;
            }
            if (lower.endsWith(".pcm")) {
                return InputFormatEnum.PCM;
            }
            if (lower.endsWith(".mp3")) {
                throw new IllegalArgumentException(
                        "实时识别 SDK 不支持 MP3，请转为 16kHz wav/pcm 或改用录音文件识别");
            }
        }
        return InputFormatEnum.WAV;
    }

    private static InputFormatEnum parseFormat(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "pcm" -> InputFormatEnum.PCM;
            case "wav" -> InputFormatEnum.WAV;
            case "opus", "webm", "ogg" -> InputFormatEnum.OPUS;
            case "mp3" -> throw new IllegalArgumentException("SDK InputFormatEnum 无 MP3，请使用 wav 或 pcm");
            default -> throw new IllegalArgumentException("不支持的 aliyun.default-format: " + format);
        };
    }

    private static SampleRateEnum resolveSampleRate(int sampleRate) {
        if (sampleRate == 8000) {
            return SampleRateEnum.SAMPLE_RATE_8K;
        }
        return SampleRateEnum.SAMPLE_RATE_16K;
    }

    private final class CollectingListener extends SpeechTranscriberListener {

        private final StringBuilder transcript = new StringBuilder();
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile String failureMessage;

        @Override
        public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
        }

        @Override
        public void onTranscriberStart(SpeechTranscriberResponse response) {
            log.debug("阿里云 NLS 识别开始 taskId={}", response != null ? response.getTaskId() : null);
        }

        @Override
        public void onSentenceBegin(SpeechTranscriberResponse response) {
        }

        @Override
        public void onSentenceEnd(SpeechTranscriberResponse response) {
            if (response != null && StringUtils.hasText(response.getTransSentenceText())) {
                if (!transcript.isEmpty()) {
                    transcript.append('\n');
                }
                transcript.append(response.getTransSentenceText().trim());
            }
        }

        @Override
        public void onTranscriptionComplete(SpeechTranscriberResponse response) {
            latch.countDown();
        }

        @Override
        public void onFail(SpeechTranscriberResponse response) {
            if (response != null) {
                failureMessage = response.getStatusText();
                log.warn("阿里云 NLS onFail status={} text={}", response.getStatus(), failureMessage);
            } else {
                failureMessage = "unknown";
            }
            latch.countDown();
        }

        boolean await(int timeoutMs) throws InterruptedException {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }
}
