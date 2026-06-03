package com.smartapply.ida.common.mybatis;

/**
 * 与库表 {@code deleted_at}（NULL=未删，非空=删除时间）配套的 MyBatis-Plus 逻辑删除取值。
 */
public final class IdaTableLogic {

    private IdaTableLogic() {
    }

    /** 未删除：查询条件为 {@code deleted_at IS NULL}。 */
    public static final String NOT_DELETED = "null";

    /** 删除：更新为 {@code deleted_at = now()}。 */
    public static final String DELETED = "now()";
}
