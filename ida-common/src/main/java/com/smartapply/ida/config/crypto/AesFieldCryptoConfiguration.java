package com.smartapply.ida.config.crypto;

import com.smartapply.ida.config.props.crypto.AesFieldCryptoProperties;
import com.smartapply.ida.security.crypto.field.AesStrongStringCipher;
import com.smartapply.ida.security.crypto.field.DeterministicAesStringCipher;
import com.smartapply.ida.security.crypto.field.NoOpReversibleStringCipher;
import com.smartapply.ida.security.crypto.field.ReversibleStringCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;

/**
 * 可逆字段加密（手机号、邮箱等）的 Spring 装配：与 {@link BcryptPasswordCryptoConfiguration} 职责分离。
 * <p>
 * 关闭 {@code enabled} 时注册 {@link NoOpReversibleStringCipher}，便于本地联调；生产务必开启并配置密钥。
 */
@Slf4j
@Configuration
public class AesFieldCryptoConfiguration {

    /**
     * 主加密 bean（随机 IV）：{@code @Primary} 保证按类型注入（如 {@code EmailCrypto}、
     * {@code AesStringTypeHandlerSpringBridge}）时不受确定性 bean 影响。
     */
    @Bean
    @Primary
    public ReversibleStringCipher reversibleStringCipher(AesFieldCryptoProperties properties) {
        if (!properties.isEnabled()) {
            log.warn("ida.crypto.aes-field.enabled=false，手机号/邮箱将以明文策略透传（NoOp），切勿用于生产");
            return new NoOpReversibleStringCipher();
        }
        String secret = properties.getSecret() == null ? "" : properties.getSecret().trim();
        String saltHex = properties.getSaltHex() == null ? "" : properties.getSaltHex().trim();
        // 开启加密却未配 secret：启动失败优于运行期读到明文库却以为已加密
        if (secret.isEmpty()) {
            throw new IllegalStateException("ida.crypto.aes-field.enabled=true 时必须配置 ida.crypto.aes-field.secret（建议环境变量 IDA_AES_FIELD_SECRET）");
        }
        validateSaltHex(saltHex);
        // Encryptors.stronger：与 Spring Security 文档一致，内部 AES-256 + 随机 IV 等
        BytesEncryptor encryptor = Encryptors.stronger(secret, saltHex);
        log.info("已启用 AES 字段加密（Encryptors.stronger）");
        return new AesStrongStringCipher(encryptor);
    }

    /**
     * 确定性加密（固定 IV，同一明文恒得同一密文）：仅用于必须支持数据库等值查询的字段，
     * 当前为登录手机号 {@code ida_user.phone}（UserAuthServiceImpl 采用 encrypt-then-eq 查询）。
     * 随机 IV 密文每次不同，会导致等值查询永远查不到，故必须独立于此 bean。
     */
    @Bean("deterministicStringCipher")
    public ReversibleStringCipher deterministicStringCipher(AesFieldCryptoProperties properties) {
        if (!properties.isEnabled()) {
            return new NoOpReversibleStringCipher();
        }
        String secret = properties.getSecret() == null ? "" : properties.getSecret().trim();
        String saltHex = properties.getSaltHex() == null ? "" : properties.getSaltHex().trim();
        log.info("已启用确定性 AES 字段加密（用于手机号等值查询）");
        return new DeterministicAesStringCipher(secret, saltHex);
    }

    /**
     * Spring 要求 salt 为十六进制且长度为 8 字节（16 个 hex 字符）。
     */
    private static void validateSaltHex(String saltHex) {
        if (saltHex.length() != 16) {
            throw new IllegalStateException("ida.crypto.aes-field.salt-hex 必须为 16 位十六进制字符串（8 字节），当前长度: " + saltHex.length());
        }
        if (!saltHex.matches("[0-9a-fA-F]{16}")) {
            throw new IllegalStateException("ida.crypto.aes-field.salt-hex 只能包含 0-9 a-f A-F");
        }
    }
}
