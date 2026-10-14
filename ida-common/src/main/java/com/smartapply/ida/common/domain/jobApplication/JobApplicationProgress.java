package com.smartapply.ida.common.domain.jobApplication;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 投递进度：库表 {@code ida_job_application.progress} 存 {@link #getCode()}，{@link #getLabel()} 供前端展示。
 */
@Getter
@AllArgsConstructor
public enum JobApplicationProgress {

    APPLIED("APPLIED", "已投递"),
    ASSESSMENT("ASSESSMENT", "测评"),
    SCREENING("SCREENING", "初筛"),
    RESCREEN("RESCREEN", "复筛"),
    WRITTEN_TEST("WRITTEN_TEST", "笔试"),
    INTERVIEW_1("INTERVIEW_1", "一面"),
    INTERVIEW_2("INTERVIEW_2", "二面"),
    FINAL_INTERVIEW("FINAL_INTERVIEW", "终面"),
    OFFER("OFFER", "offer"),
    TERMINATED("TERMINATED", "终止");

    private final String code;
    private final String label;

    /** 按 code 查找，未命中返回 null。 */
    public static JobApplicationProgress fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (JobApplicationProgress p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return null;
    }

    /** code → 展示文案，未命中返回 null。 */
    public static String labelOf(String code) {
        JobApplicationProgress p = fromCode(code);
        return p == null ? null : p.getLabel();
    }
}
