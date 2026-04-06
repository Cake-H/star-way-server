package com.smartapply.ida.security.crypto.phone;

import com.smartapply.ida.security.crypto.field.ReversibleStringCipher;
import org.springframework.stereotype.Component;

/**
 * 手机号加解密封装；算法由注入的 {@link ReversibleStringCipher} 决定（生产为 AES，开发可为 NoOp）。
 */
@Component
public class PhoneCrypto {

    private final ReversibleStringCipher cipher;

    public PhoneCrypto(ReversibleStringCipher reversibleStringCipher) {
        this.cipher = reversibleStringCipher;
    }

    /** 入库、按号查询前调用；空串原样返回 */
    public String encrypt(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        return cipher.encrypt(phone.trim());
    }

    /** 展示或日志脱敏前从密文还原；与 {@link #encrypt} 对称 */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        return cipher.decrypt(stored.trim());
    }
}
