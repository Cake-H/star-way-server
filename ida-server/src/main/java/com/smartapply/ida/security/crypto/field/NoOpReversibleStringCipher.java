package com.smartapply.ida.security.crypto.field;

/**
 * 开发环境占位：不解密，直接透传。生产环境请在配置中启用 {@code ida.crypto.aes-field}。
 */
public class NoOpReversibleStringCipher implements ReversibleStringCipher {

    @Override
    public String encrypt(String plainText) {
        return plainText;
    }

    @Override
    public String decrypt(String cipherText) {
        return cipherText;
    }
}
