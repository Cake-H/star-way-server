package com.smartapply.ida.common.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ida_resume_skill")
public class ResumeSkill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String skillCategory;
    private String skillName;
    private String proficiency;
    private Integer monthsOfUse;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
