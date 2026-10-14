package com.smartapply.ida.module.app.jobApplication.assembler;

import com.smartapply.ida.common.domain.jobApplication.JobApplication;
import com.smartapply.ida.common.domain.jobApplication.JobApplicationProgress;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationListItemVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationProgressOptionVO;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 投递记录 Entity → 对外 VO：库表 {@code id} 映射为 {@code applicationId}，日期格式化为字符串。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobApplicationViewAssembler {

    public static JobApplicationListItemVO toListItem(JobApplication a) {
        return JobApplicationListItemVO.builder()
                .applicationId(a.getId())
                .companyName(a.getCompanyName())
                .position(a.getPosition())
                .applyDate(fmtDate(a.getApplyDate()))
                .progress(a.getProgress())
                .progressLabel(JobApplicationProgress.labelOf(a.getProgress()))
                .applyUrl(a.getApplyUrl())
                .source(a.getSource())
                .remark(a.getRemark())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    /** 投递进度枚举 → 下拉选项列表（按枚举声明顺序）。 */
    public static List<JobApplicationProgressOptionVO> toProgressOptions() {
        return Arrays.stream(JobApplicationProgress.values())
                .map(p -> JobApplicationProgressOptionVO.builder()
                        .code(p.getCode())
                        .label(p.getLabel())
                        .build())
                .toList();
    }

    /** 请求参数中的日期（YYYY-MM-DD）→ {@link LocalDate}，空串返回 null。 */
    public static LocalDate parseApplyDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDate.parse(s.trim());
    }

    private static String fmtDate(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
