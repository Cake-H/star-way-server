package com.smartapply.ida.module.admin.plugin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasCandidateVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasUpsertRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedback;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackStatVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.plugin.service.AdminPluginAutofillService;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillAliasMapper;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台插件一键填表服务实现：全局反馈聚合 + 候选挖掘 + 动态别名审核。
 * <p>
 * 聚合写法参照 ida-plugin 的 {@code PluginAutofillFeedbackStatsService}
 * （{@code QueryWrapper.selectMaps + groupBy}），仅去掉 user_id 条件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPluginAutofillServiceImpl implements AdminPluginAutofillService {

    private static final int DEFAULT_DAYS = 30;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    /** days 上限：与反馈表 90 天保留期对齐，超过部分查不到数据且无意义 */
    private static final int MAX_DAYS = 90;

    /** 别名候选挖掘关注的失败原因 */
    private static final Set<String> ALIAS_CANDIDATE_REASONS = Set.of("option_not_found", "selector_miss");

    private static final Set<String> ALIAS_STATUSES = Set.of(
            PluginAutofillAlias.STATUS_PENDING, PluginAutofillAlias.STATUS_APPROVED, PluginAutofillAlias.STATUS_REJECTED);

    private final PluginAutofillFeedbackMapper feedbackMapper;
    private final PluginAutofillAliasMapper aliasMapper;

    // ======================== 全局统计 ========================

    @Override
    public PluginAutofillFeedbackStatVO stats(Integer days, String siteHost) {
        int d = normalizeDays(days);
        LocalDateTime since = LocalDateTime.now().minusDays(d);

        PluginAutofillFeedbackStatVO vo = new PluginAutofillFeedbackStatVO();
        vo.setDays(d);
        try {
            vo.setTotalFields(count(since, siteHost, null));
            vo.setFilledCount(count(since, siteHost, "filled"));
            vo.setSkippedCount(count(since, siteHost, "skipped"));
            vo.setErrorCount(count(since, siteHost, "error"));
            vo.setFillRate(vo.getTotalFields() == 0 ? 0d
                    : (double) vo.getFilledCount() / vo.getTotalFields());
            vo.setByFailReason(queryReasonCounts(since, siteHost));
            vo.setByClientFieldKey(queryFieldCounts(since, siteHost, "client_field_key"));
            vo.setBySiteHost(queryFieldCounts(since, siteHost, "site_host"));
        } catch (Exception e) {
            log.warn("[后台] 反馈全局统计查询失败: {}", e.getMessage(), e);
        }
        return vo;
    }

    // ======================== 候选挖掘 ========================

    @Override
    public List<PluginAutofillAliasCandidateVO> aliasCandidates(Integer days, Integer limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(normalizeDays(days));
        int top = normalizeLimit(limit);

        QueryWrapper<PluginAutofillFeedback> w = new QueryWrapper<>();
        w.ge("created_at", since)
                .in("fail_reason", ALIAS_CANDIDATE_REASONS)
                .isNotNull("client_field_key").ne("client_field_key", "")
                .select("client_field_key AS k, COUNT(*) AS cnt, MAX(site_host) AS sample_host")
                .groupBy("client_field_key")
                .orderByDesc("cnt")
                .last("LIMIT " + top);
        List<Map<String, Object>> maps = feedbackMapper.selectMaps(w);

        List<PluginAutofillAliasCandidateVO> out = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            PluginAutofillAliasCandidateVO vo = new PluginAutofillAliasCandidateVO();
            vo.setKey(asString(m.get("k")));
            vo.setCount(asLong(m.get("cnt")));
            vo.setSampleSiteHost(asString(m.get("sample_host")));
            out.add(vo);
        }
        return out;
    }

    @Override
    public List<PluginAutofillFeedbackStatVO.FieldCount> siteCandidates(Integer days, Integer limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(normalizeDays(days));
        int top = normalizeLimit(limit);

        QueryWrapper<PluginAutofillFeedback> w = new QueryWrapper<>();
        w.ge("created_at", since)
                .isNotNull("site_host").ne("site_host", "")
                .select("site_host AS k, COUNT(*) AS total, SUM(CASE WHEN status='filled' THEN 1 ELSE 0 END) AS filled")
                .groupBy("site_host");
        List<Map<String, Object>> maps = feedbackMapper.selectMaps(w);

        List<PluginAutofillFeedbackStatVO.FieldCount> out = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            long total = asLong(m.get("total"));
            long filled = asLong(m.get("filled"));
            PluginAutofillFeedbackStatVO.FieldCount fc = new PluginAutofillFeedbackStatVO.FieldCount();
            fc.setKey(asString(m.get("k")));
            fc.setTotal(total);
            fc.setFilled(filled);
            fc.setFillRate(total == 0 ? 0d : (double) filled / total);
            out.add(fc);
        }
        // 命中率升序：最差的站点在前，最需要建模板
        out.sort(Comparator.comparingDouble(PluginAutofillFeedbackStatVO.FieldCount::getFillRate));
        return out.size() > top ? new ArrayList<>(out.subList(0, top)) : out;
    }

    // ======================== 动态别名审核 ========================

    @Override
    public PageVO<PluginAutofillAlias> pageAliases(String status, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_LIMIT);

        LambdaQueryWrapper<PluginAutofillAlias> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(PluginAutofillAlias::getStatus, status.trim());
        }
        qw.orderByDesc(PluginAutofillAlias::getUpdatedAt);

        Page<PluginAutofillAlias> result = aliasMapper.selectPage(new Page<>(safePage, safeSize), qw);
        return PageVO.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public Long createAlias(PluginAutofillAliasUpsertRequest request) {
        String label = request.getAliasLabel().trim();
        String path = request.getFieldPath().trim();
        String status = StringUtils.hasText(request.getStatus())
                ? request.getStatus().trim() : PluginAutofillAlias.STATUS_APPROVED;
        if (!ALIAS_STATUSES.contains(status)) {
            throw new BusinessException("status 仅允许 pending/approved/rejected");
        }
        String source = StringUtils.hasText(request.getSource())
                ? request.getSource().trim() : PluginAutofillAlias.SOURCE_MANUAL;
        if (!PluginAutofillAlias.SOURCE_FEEDBACK.equals(source) && !PluginAutofillAlias.SOURCE_MANUAL.equals(source)) {
            throw new BusinessException("source 仅允许 feedback/manual");
        }

        Long exists = aliasMapper.selectCount(new LambdaQueryWrapper<PluginAutofillAlias>()
                .eq(PluginAutofillAlias::getAliasLabel, label));
        if (exists != null && exists > 0) {
            throw new BusinessException("别名已存在: " + label);
        }

        PluginAutofillAlias alias = new PluginAutofillAlias();
        alias.setAliasLabel(label);
        alias.setFieldPath(path);
        alias.setSource(source);
        alias.setStatus(status);
        alias.setRemark(request.getRemark() == null ? "" : request.getRemark().trim());
        try {
            aliasMapper.insert(alias);
        } catch (DuplicateKeyException e) {
            // selectCount 预检与 insert 之间存在 TOCTOU 窗口：并发/重复点击撞 uk_alias_label 唯一索引
            throw new BusinessException("别名已存在: " + label);
        }
        log.info("[后台] 新增动态别名 label={} path={} status={} id={}", label, path, status, alias.getId());
        return alias.getId();
    }

    @Override
    public void reviewAlias(Long id, String status) {
        if (!PluginAutofillAlias.STATUS_APPROVED.equals(status)
                && !PluginAutofillAlias.STATUS_REJECTED.equals(status)) {
            throw new BusinessException("status 仅允许 approved/rejected");
        }
        PluginAutofillAlias alias = aliasMapper.selectById(id);
        if (alias == null) {
            throw new BusinessException("别名不存在");
        }
        PluginAutofillAlias update = new PluginAutofillAlias();
        update.setId(id);
        update.setStatus(status);
        aliasMapper.updateById(update);
        log.info("[后台] 动态别名审核 id={} status={} -> {}", id, alias.getStatus(), status);
    }

    // ======================== 内部聚合查询 ========================

    private long count(LocalDateTime since, String siteHost, String status) {
        QueryWrapper<PluginAutofillFeedback> w = baseWrapper(since, siteHost);
        if (status != null) {
            w.eq("status", status);
        }
        Long c = feedbackMapper.selectCount(w);
        return c == null ? 0 : c;
    }

    private List<PluginAutofillFeedbackStatVO.ReasonCount> queryReasonCounts(LocalDateTime since, String siteHost) {
        QueryWrapper<PluginAutofillFeedback> w = baseWrapper(since, siteHost);
        w.isNotNull("fail_reason").ne("fail_reason", "")
                .select("fail_reason AS reason, COUNT(*) AS cnt")
                .groupBy("fail_reason").orderByDesc("cnt");
        List<Map<String, Object>> maps = feedbackMapper.selectMaps(w);
        List<PluginAutofillFeedbackStatVO.ReasonCount> out = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            PluginAutofillFeedbackStatVO.ReasonCount r = new PluginAutofillFeedbackStatVO.ReasonCount();
            r.setReason(asString(m.get("reason")));
            r.setCount(asLong(m.get("cnt")));
            out.add(r);
        }
        return out;
    }

    private List<PluginAutofillFeedbackStatVO.FieldCount> queryFieldCounts(
            LocalDateTime since, String siteHost, String column) {
        QueryWrapper<PluginAutofillFeedback> w = baseWrapper(since, siteHost);
        w.select(column + " AS k, COUNT(*) AS total, SUM(CASE WHEN status='filled' THEN 1 ELSE 0 END) AS filled")
                .groupBy(column).orderByDesc("total");
        List<Map<String, Object>> maps = feedbackMapper.selectMaps(w);
        List<PluginAutofillFeedbackStatVO.FieldCount> out = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            String key = asString(m.get("k"));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            long total = asLong(m.get("total"));
            long filled = asLong(m.get("filled"));
            PluginAutofillFeedbackStatVO.FieldCount fc = new PluginAutofillFeedbackStatVO.FieldCount();
            fc.setKey(key);
            fc.setTotal(total);
            fc.setFilled(filled);
            fc.setFillRate(total == 0 ? 0d : (double) filled / total);
            out.add(fc);
        }
        out.sort(Comparator.comparingDouble(PluginAutofillFeedbackStatVO.FieldCount::getFillRate));
        return out.size() > DEFAULT_LIMIT ? new ArrayList<>(out.subList(0, DEFAULT_LIMIT)) : out;
    }

    /** 全局聚合：与 C 端统计的差异就是没有 user_id 条件 */
    private QueryWrapper<PluginAutofillFeedback> baseWrapper(LocalDateTime since, String siteHost) {
        QueryWrapper<PluginAutofillFeedback> w = new QueryWrapper<>();
        w.ge("created_at", since);
        if (StringUtils.hasText(siteHost)) {
            w.eq("site_host", siteHost.trim().toLowerCase());
        }
        return w;
    }

    private static int normalizeDays(Integer days) {
        return days == null || days <= 0 ? DEFAULT_DAYS : Math.min(days, MAX_DAYS);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static long asLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
