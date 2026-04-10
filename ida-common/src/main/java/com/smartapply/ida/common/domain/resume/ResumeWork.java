package com.smartapply.ida.common.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ida_resume_work")
public class ResumeWork {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String companyName;
    private String department;
    private String jobTitle;
    private String workCity;
    private String companyIndustry;
    private String employmentNature;
    private LocalDate startDate;
    private LocalDate endDate;
    private String workDescription;
    private String leaveReason;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
