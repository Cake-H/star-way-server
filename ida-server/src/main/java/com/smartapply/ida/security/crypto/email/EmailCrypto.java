package com.smartapply.ida.security.crypto.email;

import com.smartapply.ida.security.crypto.field.ReversibleStringCipher;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 邮箱加解密封装；写入前统一小写+去空格，便于唯一性比对与填充一致性。
 */
@Component
public class EmailCrypto {

    private final ReversibleStringCipher cipher;

    public EmailCrypto(ReversibleStringCipher reversibleStringCipher) {
        this.cipher = reversibleStringCipher;
    }

    /** 先规范化（trim + 小写）再加密，保证同一邮箱只对应一条密文 */
    public String encrypt(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return cipher.encrypt(normalized);
    }

    /** 解密后为规范化后的明文（小写） */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        return cipher.decrypt(stored.trim());
    }
}
