package com.smartapply.ida.common.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ida_resume_language")
public class ResumeLanguage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String languageName;
    private String listenLevel;
    private String speakLevel;
    private String readLevel;
    private String writeLevel;
    private String certName;
    private String certScore;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
