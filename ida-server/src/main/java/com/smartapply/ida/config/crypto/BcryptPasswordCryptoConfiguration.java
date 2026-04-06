package com.smartapply.ida.config.crypto;

import com.smartapply.ida.config.props.crypto.BcryptPasswordProperties;
import com.smartapply.ida.security.crypto.password.BcryptPasswordCrypto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 登录密码相关 Bean：{@link PasswordEncoder}（BCrypt）+ 业务侧封装 {@link BcryptPasswordCrypto}。
 */
@Configuration
public class BcryptPasswordCryptoConfiguration {

    /**
     * 标准 {@link BCryptPasswordEncoder}，强度由 {@code ida.crypto.bcrypt.strength} 控制。
     */
    @Bean
    public PasswordEncoder passwordEncoder(BcryptPasswordProperties properties) {
        int strength = properties.getStrength();
        if (strength < 4 || strength > 31) {
            throw new IllegalStateException("ida.crypto.bcrypt.strength 必须在 4–31 之间，当前: " + strength);
        }
        return new BCryptPasswordEncoder(strength);
    }

    /**
     * 对明文密码做 encode/matches 的薄封装，便于单测替换 {@link PasswordEncoder}。
     */
    @Bean
    public BcryptPasswordCrypto bcryptPasswordCrypto(PasswordEncoder passwordEncoder) {
        return new BcryptPasswordCrypto(passwordEncoder);
    }
}
