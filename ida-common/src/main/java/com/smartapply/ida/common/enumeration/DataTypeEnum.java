package com.smartapply.ida.common.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应体 data 的展示类型（预留：若以后做接口加密可扩展 ENCRYPT 等）。
 */
@Getter
@AllArgsConstructor
public enum DataTypeEnum {

    NORMAL(1, "普通 JSON 数据");

    private final Integer value;
    private final String desc;
}
