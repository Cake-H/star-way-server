package com.smartapply.ida.common.domain.jobApplication;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartapply.ida.common.mybatis.IdaTableLogic;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投递记录表 {@code ida_job_application}，一行对应用户对某公司某岗位的一次投递。
 */
@Data
@TableName("ida_job_application")
public class JobApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String companyName;
    private String position;
    private LocalDate applyDate;
    private String progress;
    private String applyUrl;
    private String source;
    private String remark;

    @TableLogic(value = IdaTableLogic.NOT_DELETED, delval = IdaTableLogic.DELETED)
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
