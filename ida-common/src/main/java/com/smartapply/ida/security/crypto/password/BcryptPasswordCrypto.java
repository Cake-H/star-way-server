package com.smartapply.ida.security.crypto.password;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 登录密码专用：单向哈希，不提供解密。使用 Spring Security {@link PasswordEncoder}（BCrypt）。
 */
public class BcryptPasswordCrypto {

    private final PasswordEncoder passwordEncoder;

    public BcryptPasswordCrypto(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /** 注册或改密时写入库字段 */
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /** 登录比对；encoded 为空视为无密码记录 */
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
