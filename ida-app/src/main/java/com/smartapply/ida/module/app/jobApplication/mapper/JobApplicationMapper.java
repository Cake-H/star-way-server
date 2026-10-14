package com.smartapply.ida.module.app.jobApplication.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.jobApplication.JobApplication;
import org.apache.ibatis.annotations.Mapper;

/** 表 {@code ida_job_application} */
@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {
}
