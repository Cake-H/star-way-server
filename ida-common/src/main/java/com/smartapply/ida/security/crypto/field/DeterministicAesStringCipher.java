package com.smartapply.ida.security.crypto.field;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * 确定性 AES 字符串加密：底层使用 Spring Security {@link Encryptors#text(String, String)}，
 * 其内部为固定 IV 的 AES-256-CBC，因此<b>同一明文恒得同一密文</b>，可支持数据库等值查询（如登录手机号 eq 匹配）。
 * <p>
 * 安全性说明：确定性加密会暴露明文的相等关系，仅用于必须支持等值查询的字段（如 user.phone）；
 * 其余无需等值查询的敏感字段请使用随机 IV 的 {@link AesStrongStringCipher}。
 */
public class DeterministicAesStringCipher implements ReversibleStringCipher {

    private final TextEncryptor encryptor;

    /**
     * @param secret  主口令（与随机 IV 版共用同一组密钥材料）
     * @param saltHex 16 位十六进制盐（8 字节）
     */
    public DeterministicAesStringCipher(String secret, String saltHex) {
        this.encryptor = Encryptors.text(secret, saltHex);
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        return encryptor.encrypt(plainText);
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        return encryptor.decrypt(cipherText);
    }
}
