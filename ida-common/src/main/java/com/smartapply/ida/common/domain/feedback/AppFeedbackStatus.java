package com.smartapply.ida.common.domain.feedback;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反馈处理状态：库表 {@code ida_app_feedback.status} 存 {@link #getCode()}，{@link #getLabel()} 供前端展示。
 */
@Getter
@AllArgsConstructor
public enum AppFeedbackStatus {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    RESOLVED("RESOLVED", "已解决");

    private final String code;
    private final String label;

    /** 按 code 查找，未命中返回 null。 */
    public static AppFeedbackStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AppFeedbackStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }

    /** code → 展示文案，未命中返回 null。 */
    public static String labelOf(String code) {
        AppFeedbackStatus s = fromCode(code);
        return s == null ? null : s.getLabel();
    }
}
