package com.smartapply.ida.security.crypto.mybatis;

import com.smartapply.ida.security.crypto.field.ReversibleStringCipher;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 简历敏感字段 AES 透明加解密 TypeHandler（隐私安全基线 1.4）。
 *
 * <p>挂接方式：实体类 {@code @TableName(autoResultMap = true)} + 字段
 * {@code @TableField(typeHandler = AesStringTypeHandler.class)}，读写 SQL 无需任何改动。
 *
 * <p><b>注意：</b>本类<b>不能</b>注册为 Spring Bean——MyBatis-Plus 自动配置会收集容器中的
 * TypeHandler 并全局注册为 String 类型的默认处理器，导致所有字符串字段被误加解密。
 * MyBatis 通过无参构造自行实例化本类；加解密所需的 {@link ReversibleStringCipher} 由
 * {@link AesStringTypeHandlerSpringBridge} 在容器启动后写入 static 字段。
 * static 字段为空（非 Spring 环境）时加解密均原样透传。
 *
 * <p>存量明文兼容：解密失败（Base64 非法、密钥不符、历史明文行）时原样返回库中值，
 * 保证开启加密前写入的旧数据仍可读取。
 */
@Slf4j
public class AesStringTypeHandler extends BaseTypeHandler<String> {

    private static volatile ReversibleStringCipher cipherHolder;

    /** MyBatis 实例化入口（无参构造） */
    public AesStringTypeHandler() {
    }

    /** 由 {@link AesStringTypeHandlerSpringBridge} 在容器就绪后调用 */
    public static void setCipher(ReversibleStringCipher cipher) {
        cipherHolder = cipher;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    private static String encrypt(String plain) {
        ReversibleStringCipher cipher = cipherHolder;
        if (cipher == null || plain == null || plain.isEmpty()) {
            return plain;
        }
        return cipher.encrypt(plain);
    }

    private static String decrypt(String stored) {
        ReversibleStringCipher cipher = cipherHolder;
        if (cipher == null || stored == null || stored.isEmpty()) {
            return stored;
        }
        try {
            return cipher.decrypt(stored);
        } catch (Exception e) {
            // 存量明文行 / 密钥不符：原样返回，兼容开启加密前的数据
            log.debug("字段解密失败按原值返回（可能是存量明文）: {}", e.getMessage());
            return stored;
        }
    }
}
