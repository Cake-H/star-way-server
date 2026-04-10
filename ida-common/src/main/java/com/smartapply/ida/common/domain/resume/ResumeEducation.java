package com.smartapply.ida.common.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ida_resume_education")
public class ResumeEducation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String schoolName;
    private String collegeName;
    private String major;
    private String degree;
    private String academicDegree;
    private Integer isFullTime;
    private LocalDate enrollDate;
    private LocalDate graduateDate;
    private String countryRegion;
    private String gpa;
    private String rankingDesc;
    private String majorCourses;
    private String studentPosition;
    private String honors;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
