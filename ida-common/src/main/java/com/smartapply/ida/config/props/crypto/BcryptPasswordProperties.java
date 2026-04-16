package com.smartapply.ida.config.props.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录密码哈希（BCrypt）参数，与 {@link com.smartapply.ida.security.crypto.password.BcryptPasswordCrypto} 对应。
 * <p>
 * 强度、算法细节见类内注释；默认值写在字段上，避免在 yml 堆叠说明。
 */
@Data
@ConfigurationProperties(prefix = "ida.crypto.bcrypt")
public class BcryptPasswordProperties {

    /**
     * BCrypt 对数成本因子，合法范围约 4–31，默认 10；越大越慢越抗暴力。
     */
    private int strength = 10;
}
