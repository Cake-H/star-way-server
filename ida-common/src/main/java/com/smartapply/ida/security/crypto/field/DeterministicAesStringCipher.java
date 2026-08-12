package com.smartapply.ida.security.crypto.field;

import org.springframework.security.crypto.encrypt.AesBytesEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 确定性 AES 字符串加密：<b>同一明文恒得同一密文</b>，可支持数据库等值查询（如登录手机号 eq 匹配）。
 * <p>
 * 实现说明：底层为 Spring Security {@link AesBytesEncryptor} 的双参构造（固定 IV 的 AES-CBC），
 * 密文按 hex 编码。已实测（spring-security-crypto 6.2.0）：同密钥材料跨实例/跨重启密文一致。
 * <p>
 * 注意：{@code Encryptors.text()} / {@code Encryptors.standard()} 在 Spring Security 6.x 均为
 * <b>随机 IV</b>（5.x 的确定性方法 {@code queryableText} 已被移除），不可用于本场景；
 * {@code HexEncodingTextEncryptor} 为包私有，故 hex 编解码用 JDK {@link HexFormat} 自行处理。
 * <p>
 * 安全性说明：确定性加密会暴露明文的相等关系，仅用于必须支持等值查询的字段（如 user.phone）；
 * 其余无需等值查询的敏感字段请使用随机 IV 的 {@link AesStrongStringCipher}。
 */
public class DeterministicAesStringCipher implements ReversibleStringCipher {

    private final AesBytesEncryptor encryptor;

    /**
     * @param secret  主口令（与随机 IV 版共用同一组密钥材料）
     * @param saltHex 16 位十六进制盐（8 字节）
     */
    public DeterministicAesStringCipher(String secret, String saltHex) {
        this.encryptor = new AesBytesEncryptor(secret, saltHex);
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        return HexFormat.of().formatHex(encryptor.encrypt(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        return new String(encryptor.decrypt(HexFormat.of().parseHex(cipherText)), StandardCharsets.UTF_8);
    }
}
