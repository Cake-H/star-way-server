package com.smartapply.ida.common.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ida_resume_certificate")
public class ResumeCertificate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String certName;
    private String issuer;
    private LocalDate obtainDate;
    private String certNo;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
