package com.smartapply.ida.config.props.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 手机号、邮箱等可逆加密（Spring Security {@code Encryptors.stronger}）所需密钥材料。
 * <p>
 * {@code salt-hex} 必须为 <b>16 位十六进制</b>（表示 8 字节），与官方要求一致。
 * 密钥与盐请通过环境变量注入，勿提交仓库。
 */
@Data
@ConfigurationProperties(prefix = "ida.crypto.aes-field")
public class AesFieldCryptoProperties {

    /**
     * 是否启用 AES 字段加密；false 时使用 {@link com.smartapply.ida.security.crypto.field.NoOpReversibleStringCipher}（明文透传，仅便于本地开发）。
     */
    private boolean enabled = false;

    /**
     * 主口令，映射环境变量 {@code IDA_AES_FIELD_SECRET}。
     */
    private String secret = "";

    /**
     * 盐值（16 位 hex），映射 {@code IDA_AES_FIELD_SALT_HEX}。
     */
    private String saltHex = "";
}
