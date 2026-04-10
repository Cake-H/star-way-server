package com.smartapply.ida.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * 大模型对「多值」字段既可能输出单个字符串，也可能输出字符串数组，统一反序列化为 {@link String}（数组元素用中文分号拼接）。
 */
public class StringOrStringListDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return fromNode(node);
    }

    static String fromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : node) {
                String part = null;
                if (n.isTextual()) {
                    part = n.asText();
                } else if (n.isNumber() || n.isBoolean()) {
                    part = n.asText();
                } else if (!n.isNull()) {
                    part = n.toString();
                }
                if (part != null && !part.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('；');
                    }
                    sb.append(part.trim());
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.isObject() ? node.toString() : null;
    }
}
