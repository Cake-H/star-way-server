package com.smartapply.ida.security.crypto.field;

/**
 * 可逆字符串加解密（用于手机号、邮箱等需落库后还原的字段）。
 */
public interface ReversibleStringCipher {

    /** 明文 → 密文（如 Base64 串），实现类需与 {@link #decrypt} 互逆 */
    String encrypt(String plainText);

    /** 密文 → 明文；非法密文由实现类决定抛异常或返回原串 */
    String decrypt(String cipherText);
}
