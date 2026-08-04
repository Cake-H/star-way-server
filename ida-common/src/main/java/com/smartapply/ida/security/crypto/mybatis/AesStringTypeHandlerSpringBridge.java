package com.smartapply.ida.security.crypto.mybatis;

import com.smartapply.ida.security.crypto.field.ReversibleStringCipher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link AesStringTypeHandler} 的 Spring 桥：TypeHandler 本体由 MyBatis 无参实例化（不能成为 Bean，
 * 否则会被 MyBatis-Plus 全局注册为 String 默认处理器），此处把容器中的加密组件注入其 static 持有者。
 * <p>
 * 注入的是<b>随机 IV</b> 版主 bean（reversibleStringCipher）：简历敏感字段无等值查询需求，
 * 随机 IV 安全性更高。等值查询场景（登录手机号）走服务层 {@code PhoneCrypto}（确定性加密）。
 */
@Component
@RequiredArgsConstructor
public class AesStringTypeHandlerSpringBridge {

    private final ReversibleStringCipher reversibleStringCipher;

    @PostConstruct
    void init() {
        AesStringTypeHandler.setCipher(this.reversibleStringCipher);
    }
}
