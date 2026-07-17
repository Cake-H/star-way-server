package com.smartapply.ida.module.plugin.resume.autofill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedback;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackStatVO;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 一键填表反馈统计（用户级）：按当前 userId 聚合近 N 天的反馈，返回命中率与 Top 失败原因/字段/站点。
 * <p>
 * 直接对明细表 {@code GROUP BY} 查询，数据量上来后可换聚合表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAutofillFeedbackStatsService {

    private static final int TOP_N = 20;

    private final PluginAutofillFeedbackMapper feedbackMapper;

    /**
     * @param userId   当前用户
     * @param days     统计近 N 天，默认 30
     * @param siteHost 可选，按站点过滤
     */
    public PluginAutofillFeedbackStatVO stats(long userId, Integer days, String siteHost) {
        int d = days == null || days <= 0 ? 30 : days;
        LocalDateTime since = LocalDateTime.now().minusDays(d);

        PluginAutofillFeedbackStatVO vo = new PluginAutofillFeedbackStatVO();
        vo.setDays(d);

        try {
            vo.setTotalFields(count(userId, since, siteHost, null));
            vo.setFilledCount(count(userId, since, siteHost, "filled"));
            vo.setSkippedCount(count(userId, since, siteHost, "skipped"));
            vo.setErrorCount(count(userId, since, siteHost, "error"));
            vo.setFillRate(vo.getTotalFields() == 0 ? 0d
                    : (double) vo.getFilledCount() / vo.getTotalFields());

            vo.setByFailReason(queryReasonCounts(userId, since, siteHost));
            vo.setByClientFieldKey(queryFieldCounts(userId, since, siteHost, "client_field_key"));
            vo.setBySiteHost(queryFieldCounts(userId, since, siteHost, "site_host"));
        } catch (Exception e) {
            log.warn("反馈统计查询失败 userId={}: {}", userId, e.getMessage(), e);
        }
        return vo;
    }

    private long count(long userId, LocalDateTime since, String siteHost, String status) {
        QueryWrapper<PluginAutofillFeedback> w = baseWrapper(userId, since, siteHost);
        if (status != null) {
            w.eq("status", status);
        }
        Long c = feedbackMapper.selectCount(w);
        return c == null ? 0 : c;
    }

    private List<PluginAutofillFeedbackStatVO.ReasonCount> queryReasonCounts(
            long userId, LocalDateTime since, String siteHost) {
        QueryWrapper<PluginAutofillFeedback> w = baseWrapper(userId, since, siteHost);
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
            long userId, LocalDateTime since, String siteHost, String column) {
        QueryWrapper<PluginAutofillFeedback> w = baseWrapper(userId, since, siteHost);
        w.select(column + " AS k, COUNT(*) AS total, SUM(CASE WHEN status='filled' THEN 1 ELSE 0 END) AS filled")
                .groupBy(column).orderByDesc("total");
        List<Map<String, Object>> maps = feedbackMapper.selectMaps(w);
        List<PluginAutofillFeedbackStatVO.FieldCount> out = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            String key = asString(m.get("k"));
            if (!StringUtils.hasText(key)) {
                continue; // 跳过空 key（未采集到 host/fieldKey 的行）
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
        // 命中率升序：最该优化的字段在前
        out.sort(Comparator.comparingDouble(PluginAutofillFeedbackStatVO.FieldCount::getFillRate));
        if (out.size() > TOP_N) {
            return new ArrayList<>(out.subList(0, TOP_N));
        }
        return out;
    }

    private QueryWrapper<PluginAutofillFeedback> baseWrapper(long userId, LocalDateTime since, String siteHost) {
        QueryWrapper<PluginAutofillFeedback> w = new QueryWrapper<>();
        w.eq("user_id", userId).ge("created_at", since);
        if (StringUtils.hasText(siteHost)) {
            w.eq("site_host", siteHost.trim().toLowerCase());
        }
        return w;
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
