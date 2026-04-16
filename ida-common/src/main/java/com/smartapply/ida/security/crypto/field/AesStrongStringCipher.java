package com.smartapply.ida.security.crypto.field;

import org.springframework.security.crypto.encrypt.BytesEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 基于 Spring Security {@link BytesEncryptor}（内部 256 位 AES + 标准盐派生）的字符串封装，密文使用 Base64 便于存 VARCHAR。
 */
public class AesStrongStringCipher implements ReversibleStringCipher {

    private final BytesEncryptor encryptor;

    public AesStrongStringCipher(BytesEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        byte[] enc = encryptor.encrypt(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(enc);
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        byte[] raw = Base64.getDecoder().decode(cipherText);
        return new String(encryptor.decrypt(raw), StandardCharsets.UTF_8);
    }
}
