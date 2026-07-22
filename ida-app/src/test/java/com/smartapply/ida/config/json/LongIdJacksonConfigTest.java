package com.smartapply.ida.config.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 雪花 ID 序列化保护单测：ID 语义字段输出字符串、数值字段保持数字、字符串 ID 可反序列化回 Long。
 * <p>
 * 配置类位于 ida-common（无测试依赖），故测试放在 ida-app 模块。
 */
class LongIdJacksonConfigTest {

    /** 19 位雪花 ID，超出 JS Number 安全整数上限 2^53-1 */
    private static final long SNOWFLAKE_ID = 2040700730745917441L;

    /** 模拟典型 VO：ID 字段 + 计数/分页/时间戳数值字段 */
    @SuppressWarnings("unused")
    static class SampleVO {
        public Long id = SNOWFLAKE_ID;
        public Long userId = SNOWFLAKE_ID;
        public Long usedBy = SNOWFLAKE_ID;
        public Long callCount = 35L;
        public Long total = 100L;
        public long createdAtEpochMs = 1753190400000L;
    }

    /** 用与 Spring Boot 相同的方式构建 ObjectMapper 并应用定制器 */
    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new LongIdJacksonConfig().longIdToStringCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void idLikeFields_shouldSerializeAsString() throws Exception {
        String json = mapper().writeValueAsString(new SampleVO());

        assertThat(json).contains("\"id\":\"2040700730745917441\"");
        assertThat(json).contains("\"userId\":\"2040700730745917441\"");
        assertThat(json).contains("\"usedBy\":\"2040700730745917441\"");
    }

    @Test
    void numericFields_shouldStayNumber() throws Exception {
        String json = mapper().writeValueAsString(new SampleVO());

        assertThat(json).contains("\"callCount\":35");
        assertThat(json).contains("\"total\":100");
        assertThat(json).contains("\"createdAtEpochMs\":1753190400000");
    }

    @Test
    void stringId_shouldDeserializeBackToLong() throws Exception {
        SampleVO vo = mapper().readValue("{\"id\":\"2040700730745917441\"}", SampleVO.class);

        assertThat(vo.id).isEqualTo(SNOWFLAKE_ID);
    }

    @Test
    void isIdLike_namingRules() {
        assertThat(LongIdJacksonConfig.isIdLike("id")).isTrue();
        assertThat(LongIdJacksonConfig.isIdLike("userId")).isTrue();
        assertThat(LongIdJacksonConfig.isIdLike("sessionId")).isTrue();
        assertThat(LongIdJacksonConfig.isIdLike("usedBy")).isTrue();
        assertThat(LongIdJacksonConfig.isIdLike("callCount")).isFalse();
        assertThat(LongIdJacksonConfig.isIdLike("total")).isFalse();
        assertThat(LongIdJacksonConfig.isIdLike("createdAtEpochMs")).isFalse();
    }
}
