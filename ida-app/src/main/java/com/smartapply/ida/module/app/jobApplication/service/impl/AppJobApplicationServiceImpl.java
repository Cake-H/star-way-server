package com.smartapply.ida.module.app.jobApplication.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.jobApplication.JobApplication;
import com.smartapply.ida.common.domain.jobApplication.JobApplicationProgress;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationCreateRequest;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationQueryRequest;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationUpdateRequest;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationIdVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationListItemVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationProgressOptionVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.jobApplication.assembler.JobApplicationViewAssembler;
import com.smartapply.ida.module.app.jobApplication.mapper.JobApplicationMapper;
import com.smartapply.ida.module.app.jobApplication.service.AppJobApplicationService;
import com.smartapply.ida.module.app.jobApplication.support.JobApplicationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 投递记录落库。
 * <p>
 * <b>数据策略：</b>通过 {@link com.baomidou.mybatisplus.annotation.TableLogic} 软删；单行单岗位，
 * 同一公司投多个岗位对应多行；APP 端手动创建的来源固定为 {@link #SOURCE_MANUAL}，插件识别入库写 PLUGIN。
 * <b>列表查询：</b>无分页（个人投递量为百级，与面试复盘一致），筛选条件在 {@code user_id} 收敛后组合叠加。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppJobApplicationServiceImpl implements AppJobApplicationService {

    /** 投递来源：APP 端手动记录 */
    private static final String SOURCE_MANUAL = "MANUAL";

    private final JobApplicationMapper jobApplicationMapper;
    private final JobApplicationGuard applicationGuard;

    @Override
    public List<JobApplicationListItemVO> listApplications(long userId, JobApplicationQueryRequest query) {
        LocalDate start = parseDate(query.getApplyDateStart());
        LocalDate end = parseDate(query.getApplyDateEnd());
        String progressCode = StringUtils.hasText(query.getProgress()) ? requireProgressCode(query.getProgress()) : null;

        List<JobApplication> rows = jobApplicationMapper.selectList(Wrappers.<JobApplication>lambdaQuery()
                .eq(JobApplication::getUserId, userId)
                .like(StringUtils.hasText(query.getCompanyName()), JobApplication::getCompanyName,
                        query.getCompanyName() != null ? query.getCompanyName().trim() : null)
                .like(StringUtils.hasText(query.getPosition()), JobApplication::getPosition,
                        query.getPosition() != null ? query.getPosition().trim() : null)
                .eq(progressCode != null, JobApplication::getProgress, progressCode)
                .ge(start != null, JobApplication::getApplyDate, start)
                .le(end != null, JobApplication::getApplyDate, end)
                .orderByDesc(JobApplication::getApplyDate)
                .orderByDesc(JobApplication::getCreatedAt));

        return rows.stream().map(JobApplicationViewAssembler::toListItem).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationIdVO createApplication(long userId, JobApplicationCreateRequest req) {
        LocalDateTime now = LocalDateTime.now();
        JobApplication a = new JobApplication();
        a.setUserId(userId);
        a.setCompanyName(req.getCompanyName().trim());
        a.setPosition(req.getPosition().trim());
        LocalDate applyDate = parseDate(req.getApplyDate());
        a.setApplyDate(applyDate != null ? applyDate : LocalDate.now());
        a.setProgress(StringUtils.hasText(req.getProgress())
                ? requireProgressCode(req.getProgress())
                : JobApplicationProgress.APPLIED.getCode());
        a.setApplyUrl(trimNullable(req.getApplyUrl()));
        a.setSource(SOURCE_MANUAL);
        a.setRemark(trimNullable(req.getRemark()));
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        jobApplicationMapper.insert(a);
        if (a.getId() == null) {
            throw new BusinessException("创建投递记录失败");
        }
        return JobApplicationIdVO.builder().applicationId(a.getId()).build();
    }

    /**
     * 部分更新：请求中为 null 的字段不修改；可清空字段（applyUrl/remark）传空串 trim 后落 ''，
     * applyDate 传空串清为 null。
     * <p>
     * 不用 {@code updateById}：其默认字段策略跳过 null 值，导致「清空」语义无法表达；
     * 这里改用 {@code LambdaUpdateWrapper.set(...)} 显式指定每个要更新的列。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApplication(long userId, long applicationId, JobApplicationUpdateRequest req) {
        applicationGuard.requireOwned(userId, applicationId);
        LambdaUpdateWrapper<JobApplication> uw = Wrappers.<JobApplication>lambdaUpdate()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getUserId, userId);
        if (req.getCompanyName() != null && StringUtils.hasText(req.getCompanyName())) {
            uw.set(JobApplication::getCompanyName, req.getCompanyName().trim());
        }
        if (req.getPosition() != null && StringUtils.hasText(req.getPosition())) {
            uw.set(JobApplication::getPosition, req.getPosition().trim());
        }
        if (req.getApplyDate() != null) {
            uw.set(JobApplication::getApplyDate, parseDate(req.getApplyDate()));
        }
        if (req.getProgress() != null) {
            uw.set(JobApplication::getProgress, requireProgressCode(req.getProgress()));
        }
        if (req.getApplyUrl() != null) {
            // 列 NOT NULL：清空 = 落空串，不能落 null
            uw.set(JobApplication::getApplyUrl, req.getApplyUrl().trim());
        }
        if (req.getRemark() != null) {
            uw.set(JobApplication::getRemark, req.getRemark().trim());
        }
        uw.set(JobApplication::getUpdatedAt, LocalDateTime.now());
        int updated = jobApplicationMapper.update(null, uw);
        if (updated < 1) {
            throw new BusinessException("更新投递记录失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(long userId, long applicationId) {
        applicationGuard.requireOwned(userId, applicationId);
        int deleted = jobApplicationMapper.deleteById(applicationId);
        if (deleted < 1) {
            throw new BusinessException("删除投递记录失败");
        }
    }

    @Override
    public List<JobApplicationProgressOptionVO> listProgressOptions() {
        return JobApplicationViewAssembler.toProgressOptions();
    }

    /** 进度 code 合法性校验，合法则原样返回。 */
    private static String requireProgressCode(String progress) {
        if (!StringUtils.hasText(progress) || JobApplicationProgress.fromCode(progress.trim()) == null) {
            throw new BusinessException("投递进度不合法");
        }
        return progress.trim();
    }

    private static String trimNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return JobApplicationViewAssembler.parseApplyDate(s.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("投递日期格式应为 YYYY-MM-DD");
        }
    }
}
