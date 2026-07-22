package com.smartapply.ida.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Set;

/**
 * 雪花 ID 前端精度保护。
 * <p>
 * 实体主键为 MyBatis-Plus 雪花 ID（19 位），而 JavaScript Number 的安全整数上限为
 * 2^53 - 1（约 16 位），Long 以 JSON 数字返回时末几位会被前端吞掉
 * （如 {@code 2040700730745917441} 在前端变成 {@code 2040700730745917440}）。
 * <p>
 * 规则：属性名具 ID 语义（恰为 {@code id} / 以 {@code Id} 结尾 / {@code usedBy}）的
 * {@code Long}/{@code long} 字段序列化为 JSON 字符串；计数、额度、分页、时间戳等
 * 纯数值字段（callCount、totalTokens、balance、total、createdAtEpochMs 等）保持 JSON 数字不变。
 * <p>
 * 反序列化无需配套处理：Jackson 默认支持 JSON 字符串 → Long 绑定，
 * 前端以字符串回传 ID（请求体 / 路径参数）均可正常接收。
 */
@Configuration
public class LongIdJacksonConfig {

    /** 非 Id 结尾但语义为 ID 的属性名（如「兑换码使用人」usedBy，取值为用户 ID） */
    private static final Set<String> EXTRA_ID_NAMES = Set.of("usedBy");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdToStringCustomizer() {
        LongIdSerializer serializer = new LongIdSerializer();
        // 包装类型与原始类型在 Jackson 中是两个类型查找键，必须同时注册
        return builder -> builder
                .serializerByType(Long.class, serializer)
                .serializerByType(Long.TYPE, serializer);
    }

    static boolean isIdLike(String propertyName) {
        return "id".equals(propertyName)
                || propertyName.endsWith("Id")
                || EXTRA_ID_NAMES.contains(propertyName);
    }

    /**
     * 按属性名决定输出形态的 Long 序列化器：ID 语义属性输出字符串，其余输出数字。
     * 上下文之外（Map value、根值等无法取到属性名的场景）保持数字输出。
     */
    static class LongIdSerializer extends JsonSerializer<Long> implements ContextualSerializer {

        /** true=该属性为 ID 语义，输出 JSON 字符串；false=普通数值，输出 JSON 数字 */
        private final boolean asString;

        /** 默认实例：上下文未知时按数字处理 */
        LongIdSerializer() {
            this(false);
        }

        private LongIdSerializer(boolean asString) {
            this.asString = asString;
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else if (asString) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }

        /**
         * 每个 bean 属性首次序列化时回调：命中 ID 命名规则则换成字符串形态的实例，
         * 否则沿用默认（数字）实例，避免为非 ID 属性重复创建对象。
         */
        @Override
        public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
            if (property != null && isIdLike(property.getName())) {
                return new LongIdSerializer(true);
            }
            return this;
        }
    }
}
