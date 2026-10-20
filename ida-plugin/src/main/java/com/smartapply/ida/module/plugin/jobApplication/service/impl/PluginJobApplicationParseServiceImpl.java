package com.smartapply.ida.module.plugin.jobApplication.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.ai.langchain4j.assistant.JobApplicationParseAssistant;
import com.smartapply.ida.ai.langchain4j.config.LangChain4jProperties;
import com.smartapply.ida.ai.langchain4j.prompt.JobApplicationParsePromptProvider;
import com.smartapply.ida.ai.usage.AiUsageBizType;
import com.smartapply.ida.common.domain.jobApplication.JobApplication;
import com.smartapply.ida.common.domain.jobApplication.JobApplicationProgress;
import com.smartapply.ida.common.domain.plugin.jobApplication.JobApplicationParseDraftDto;
import com.smartapply.ida.common.domain.plugin.jobApplication.dto.PluginJobApplicationParseRequest;
import com.smartapply.ida.common.domain.plugin.jobApplication.vo.PluginJobApplicationParseItemVO;
import com.smartapply.ida.common.domain.plugin.jobApplication.vo.PluginJobApplicationParseResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.jobApplication.mapper.JobApplicationMapper;
import com.smartapply.ida.module.plugin.jobApplication.service.PluginJobApplicationParseService;
import com.smartapply.ida.security.mask.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件「识别当前投递页面并入库」编排。
 * <p>
 * <b>链路：</b>页面快照规范化 → 脱敏 → {@link JobApplicationParseAssistant} 提取（{@code BillingService} 计费包裹，
 * 未识别到有效记录不扣点）→ 逐条判重落库（{@code user_id + apply_url + company_name + position}）。
 * <b>降级：</b>LLM 异常或未提取到有效记录时，退化为单条「网址 + 标题」存档（{@code parsed=false}），按钮永远可用。
 * <b>判重：</b>命中已有记录时不新建、也不回写 {@code progress}/{@code remark}，避免覆盖用户在 APP 端的推进。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PluginJobApplicationParseServiceImpl implements PluginJobApplicationParseService {

    /** 投递来源：插件识别入库 */
    private static final String SOURCE_PLUGIN = "PLUGIN";

    private final LangChain4jProperties langChain4jProperties;
    private final JobApplicationMapper jobApplicationMapper;
    private final BillingService billingService;
    private final JobApplicationParseAssistant assistant;
    private final JobApplicationParsePromptProvider promptProvider;

    @Override
    public PluginJobApplicationParseResultVO parseAndSave(Long userId, PluginJobApplicationParseRequest body) {
        // 1、规范化 + 截断
        String pageUrl = body.getPageUrl().trim();
        String pageTitle = body.getPageTitle() == null ? "" : body.getPageTitle().trim();
        String textContent = body.getTextContent() == null ? "" : body.getTextContent();
        int maxChars = langChain4jProperties.getJobApplicationParse().getMaxPlainTextChars();
        if (textContent.length() > maxChars) {
            log.info("投递记录页面正文过长，截断前 {} 字符再送入模型", maxChars);
            textContent = textContent.substring(0, maxChars);
        }

        // 2、LLM 提取（失败时 draft=null，走第 3 步降级）
        SensitiveDataMasker.MaskSession maskSession = SensitiveDataMasker.mask(textContent);
        String messageText = promptProvider.buildUserMessage(pageUrl, pageTitle, maskSession.maskedText());
        JobApplicationParseDraftDto draft = null;
        try {
            draft = billingService.execute(userId, AiUsageBizType.JOB_APPLICATION_PARSE, null,
                    () -> assistant.parse(JobApplicationParsePromptProvider.SYSTEM, messageText),
                    this::hasExtracted);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("投递记录识别调用大模型异常，降级为仅存档网址 userId={}", userId, e);
        }
        boolean parsed = hasExtracted(draft);

        // 3、组装待落库记录：LLM 失败 → 单条网址存档（公司/岗位留空，标题入备注供 APP 端辨认）
        List<JobApplicationParseDraftDto.Record> toSave = new ArrayList<>();
        if (parsed) {
            for (JobApplicationParseDraftDto.Record r : draft.getRecords()) {
                if (r != null && (StringUtils.hasText(r.getCompanyName()) || StringUtils.hasText(r.getPosition()))) {
                    toSave.add(r);
                }
            }
        }
        if (toSave.isEmpty()) {
            toSave.add(fallbackRecord());
        }

        // 4、逐条判重落库
        LocalDateTime now = LocalDateTime.now();
        List<PluginJobApplicationParseItemVO> items = new ArrayList<>();
        for (JobApplicationParseDraftDto.Record r : toSave) {
            String companyName = truncate(restore(r.getCompanyName(), maskSession), 128);
            String position = truncate(restore(r.getPosition(), maskSession), 128);
            String progress = validProgress(r.getProgress());
            LocalDate applyDate = parseDateOrToday(r.getApplyDate());

            JobApplication existing = jobApplicationMapper.selectOne(Wrappers.<JobApplication>lambdaQuery()
                    .eq(JobApplication::getUserId, userId)
                    .eq(JobApplication::getApplyUrl, pageUrl)
                    .eq(JobApplication::getCompanyName, companyName)
                    .eq(JobApplication::getPosition, position)
                    .last("LIMIT 1"));
            if (existing != null) {
                items.add(toItem(existing, true));
                continue;
            }

            JobApplication a = new JobApplication();
            a.setUserId(userId);
            a.setCompanyName(companyName);
            a.setPosition(position);
            a.setApplyDate(applyDate);
            a.setProgress(progress);
            a.setApplyUrl(pageUrl);
            a.setSource(SOURCE_PLUGIN);
            a.setRemark(parsed ? "" : truncate(pageTitle, 1024));
            a.setCreatedAt(now);
            a.setUpdatedAt(now);
            jobApplicationMapper.insert(a);
            if (a.getId() == null) {
                throw new BusinessException("保存投递记录失败");
            }
            items.add(toItem(a, false));
        }

        return PluginJobApplicationParseResultVO.builder()
                .parsed(parsed)
                .records(items)
                .build();
    }

    /** 是否提取到至少一条有效记录（兼作计费结算谓词：无有效记录不扣点）。 */
    private boolean hasExtracted(JobApplicationParseDraftDto draft) {
        if (draft == null || draft.getRecords() == null) {
            return false;
        }
        return draft.getRecords().stream().anyMatch(
                r -> r != null && (StringUtils.hasText(r.getCompanyName()) || StringUtils.hasText(r.getPosition())));
    }

    /** 降级记录：公司/岗位留空，进度/日期取默认值。 */
    private static JobApplicationParseDraftDto.Record fallbackRecord() {
        JobApplicationParseDraftDto.Record r = new JobApplicationParseDraftDto.Record();
        r.setProgress(JobApplicationProgress.APPLIED.getCode());
        return r;
    }

    private PluginJobApplicationParseItemVO toItem(JobApplication a, boolean duplicated) {
        return PluginJobApplicationParseItemVO.builder()
                .applicationId(a.getId())
                .companyName(a.getCompanyName())
                .position(a.getPosition())
                .applyDate(a.getApplyDate() == null ? null : a.getApplyDate().toString())
                .progress(a.getProgress())
                .applyUrl(a.getApplyUrl())
                .duplicated(duplicated)
                .build();
    }

    /** 进度 code 合法性兜底：模型输出非法值时回退 APPLIED，避免脏数据污染 APP 端筛选。 */
    private static String validProgress(String progress) {
        if (StringUtils.hasText(progress) && JobApplicationProgress.fromCode(progress.trim()) != null) {
            return progress.trim();
        }
        return JobApplicationProgress.APPLIED.getCode();
    }

    /** 页面常无明确投递日期：解析失败或为空时默认当天（「记录当天投递」主语义）。 */
    private static LocalDate parseDateOrToday(String applyDate) {
        if (StringUtils.hasText(applyDate)) {
            try {
                return LocalDate.parse(applyDate.trim());
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        return LocalDate.now();
    }

    /** 模型输出可能带脱敏占位符，落库前还原；null 安全。 */
    private static String restore(String s, SensitiveDataMasker.MaskSession session) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String restored = SensitiveDataMasker.restore(s.trim(), session);
        return restored == null ? "" : restored.trim();
    }

    /** 超长截断（companyName/position 列 VARCHAR(128)，严格模式下超长 insert 会报错）。 */
    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
