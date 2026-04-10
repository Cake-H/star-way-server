package com.smartapply.ida.common.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ida_resume_publication")
public class ResumePublication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String pubType;
    private String title;
    private String role;
    private String venue;
    private LocalDate publishDate;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
