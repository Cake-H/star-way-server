package com.smartapply.ida.module.plugin.auth.service;

import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.props.sms.SmsVerificationProperties;
import com.smartapply.ida.integration.sms.SmsTemplateParamJsonBuilder;
import com.smartapply.ida.integration.sms.SmsTemplateSender;
import com.smartapply.ida.security.mask.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 短信验证码核心逻辑。
 * <ul>
 *   <li>频控：同一手机号在 {@link SmsVerificationProperties#getResendIntervalSeconds()} 内仅允许发送一次；使用 Redis {@code setIfAbsent} 实现。</li>
 *   <li>存储：验证码明文仅存 Redis，键为 {@code prefix + 手机号}，TTL 见 {@link SmsVerificationProperties#getTtlSeconds()}。</li>
 *   <li>发送：模板 JSON 须含 {@code code} 与 {@code min}（有效分钟），与号码认证赠送模板变量一致，见 {@link com.smartapply.ida.integration.sms.SmsTemplateParamJsonBuilder}。</li>
 *   <li>失败回滚：短信通道异常时删除本次写入的频控键与验证码键，避免用户被锁死又收不到短信。</li>
 * </ul>
 */
@Slf4j
@Service("pluginSmsVerificationService")
@RequiredArgsConstructor
public class SmsVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final SmsVerificationProperties verificationProperties;
    private final SmsTemplateSender smsTemplateSender;

    /**
     * 生成验证码、写入 Redis 并调用短信实现类；频控未通过时抛出 {@link BusinessException}。
     * 白名单模拟号直接写固定码，跳过频控与短信通道（见 {@link SmsVerificationProperties#isMockCodeEnabled()}）。
     */
    public void sendCode(String phone) throws Exception {
        String normalized = normalizePhone(phone);
        String codeKey = verificationProperties.getRedisCodeKeyPrefix() + normalized;
        if (isMockPhone(normalized)) {
            // 模拟号：写固定码即可，登录走 verifyAndConsume 的 mock 分支，不依赖该缓存；生产请关闭 mock-code-enabled
            stringRedisTemplate.opsForValue()
                    .set(codeKey, verificationProperties.getMockCode(), Duration.ofSeconds(verificationProperties.getTtlSeconds()));
            log.warn("[MOCK] 模拟验证码已写入 phone={}（仅测试环境）", SensitiveDataMasker.maskMiddle(normalized));
            return;
        }
        String rateKey = verificationProperties.getRedisRateKeyPrefix() + normalized;
        // setIfAbsent：同一手机号在间隔内只能占坑一次，占失败说明刚发过，直接拒绝（防刷短信费）
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(rateKey, "1", Duration.ofSeconds(verificationProperties.getResendIntervalSeconds()));
        if (Boolean.FALSE.equals(first)) {
            throw new BusinessException("发送过于频繁，请 " + verificationProperties.getResendIntervalSeconds() + " 秒后再试");
        }
        String code = randomDigits(verificationProperties.getLength());
        // 先发码再调通道：失败时要删掉 rate + code，否则用户既收不到短信又进不了冷却重试
        stringRedisTemplate.opsForValue()
                .set(codeKey, code, Duration.ofSeconds(verificationProperties.getTtlSeconds()));
        String json = SmsTemplateParamJsonBuilder.buildCodeAndValidMinutesJson(code, verificationProperties.getTtlSeconds());
        try {
            smsTemplateSender.sendVerificationCode(normalized, json);
        } catch (Exception e) {
            stringRedisTemplate.delete(rateKey);
            stringRedisTemplate.delete(codeKey);
            log.warn("短信发送失败 phone={}", SensitiveDataMasker.maskMiddle(normalized), e);
            throw e;
        }
    }

    /**
     * 比对验证码，成功则删除 Redis 中的码（一次性消费）；失败不删，便于用户重试直至过期。
     * 白名单模拟号直接比对固定码，不消费、不过期，可反复登录（见 {@link SmsVerificationProperties#isMockCodeEnabled()}）。
     *
     * @return true 表示校验通过并已删除缓存
     */
    public boolean verifyAndConsume(String phone, String inputCode) {
        if (inputCode == null) {
            return false;
        }
        String normalized = normalizePhone(phone);
        if (isMockPhone(normalized)) {
            return verificationProperties.getMockCode().equals(inputCode.trim());
        }
        String codeKey = verificationProperties.getRedisCodeKeyPrefix() + normalized;
        String cached = stringRedisTemplate.opsForValue().get(codeKey);
        if (cached == null) {
            return false; // 过期或未发送
        }
        if (!cached.equals(inputCode.trim())) {
            return false; // 输错不删缓存，允许同一码多次尝试直至 TTL
        }
        stringRedisTemplate.delete(codeKey); // 校验通过立即消费，同一码不能二次登录
        return true;
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim();
    }

    /** 是否为模拟登录白名单手机号 */
    private boolean isMockPhone(String normalized) {
        return verificationProperties.isMockCodeEnabled()
                && verificationProperties.getMockPhones().contains(normalized);
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
