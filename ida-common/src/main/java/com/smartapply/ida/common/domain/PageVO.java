package com.smartapply.ida.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果包装（管理后台列表接口统一返回结构）。
 * <p>
 * 本类为纯 DTO，不依赖 MyBatis-Plus（ida-common 仅引入 mybatis-plus-annotation）；
 * 由业务层将 {@code IPage} 转换后填充。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageVO<T> {

    /** 当前页记录 */
    private List<T> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Long page;

    /** 每页大小 */
    private Long size;

    /**
     * 构造分页结果。
     *
     * @param records 当前页记录（业务层已完成实体 → VO 映射）
     * @param total   总记录数
     * @param page    当前页码
     * @param size    每页大小
     */
    public static <T> PageVO<T> of(List<T> records, long total, long page, long size) {
        return PageVO.<T>builder()
                .records(records)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }
}
