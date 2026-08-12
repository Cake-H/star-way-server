package com.smartapply.ida.ai.plugin.deterministic;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillAliasMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态字段别名服务：从表 {@code ida_plugin_autofill_alias} 加载 status=approved 的别名，
 * 作为 {@link ChineseFieldAliasTable} 静态别名表的补充，参与确定性标签匹配。
 * <p>
 * 加载策略：启动时（@PostConstruct）全量加载，之后按固定间隔全量刷新——构建不可变副本后
 * 对 volatile 引用整体赋值，读路径先取局部引用，保证刷新对读者原子可见（无 clear+putAll 中间态）。
 * 查询/刷新异常均静默降级（log.warn），匹配流程退化为仅静态表，不影响主流程。
 * <p>
 * key 统一转小写存储，resolve 入参同样转小写（中文不受影响），英文别名大小写不敏感。
 * fieldPath 在加载时用 {@link ResumeFieldAccessor#isKnownKey} 校验，非法路径丢弃并告警
 * （admin 配错不应污染匹配结果）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicFieldAliasService {

    private final PluginAutofillAliasMapper aliasMapper;

    /** normalizedLabel（trim + 小写）→ fieldPath；volatile 引用整体替换式刷新，读路径取局部引用 */
    private volatile Map<String, String> approvedAliases = Map.of();

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 全量刷新 approved 别名。fixedDelay=任务结束后等 N ms 再下次。
     */
    @Scheduled(fixedDelayString = "${ida.plugin.autofill.alias.refresh-interval-ms:300000}")
    public void refresh() {
        try {
            List<PluginAutofillAlias> rows = aliasMapper.selectList(
                    Wrappers.<PluginAutofillAlias>lambdaQuery()
                            .eq(PluginAutofillAlias::getStatus, PluginAutofillAlias.STATUS_APPROVED));
            Map<String, String> fresh = new HashMap<>();
            for (PluginAutofillAlias row : rows) {
                if (row == null || !StringUtils.hasText(row.getAliasLabel())
                        || !StringUtils.hasText(row.getFieldPath())) {
                    continue;
                }
                String path = row.getFieldPath().trim();
                if (!ResumeFieldAccessor.isKnownKey(path)) {
                    log.warn("动态别名 fieldPath 未在 ResumeFieldAccessor 注册，已丢弃: id={}, label={}, path={}",
                            row.getId(), row.getAliasLabel(), path);
                    continue;
                }
                String key = row.getAliasLabel().trim().toLowerCase();
                String prev = fresh.put(key, path);
                if (prev != null) {
                    log.warn("动态别名 aliasLabel 重复（后者覆盖前者）: label={}, 旧path={}, 新path={}", key, prev, path);
                }
            }
            // 整体替换：不可变副本一次性赋值，读者不会看到刷新中间态
            approvedAliases = Collections.unmodifiableMap(fresh);
            log.info("动态字段别名刷新完成: {} 条生效", fresh.size());
        } catch (Exception e) {
            // 降级：保留旧缓存（若有），匹配退化为仅静态表
            log.warn("动态字段别名刷新失败（保留旧缓存继续运行）: {}", e.getMessage());
        }
    }

    /**
     * 精确匹配：归一化标签（入参转小写）与别名 key 完全一致。
     *
     * @return 字段路径，未命中返回 null
     */
    public String resolveExact(String normalizedLabel) {
        if (!StringUtils.hasText(normalizedLabel)) {
            return null;
        }
        Map<String, String> aliases = approvedAliases;
        return aliases.get(normalizedLabel.trim().toLowerCase());
    }

    /**
     * 包含匹配：返回「被 label 包含的最长别名 key」对应的路径。
     * 比较前 label 与 key 均转小写；key 长度 &lt; 2 不参与（单字误命中风险高）；
     * 并列最长视为歧义返回 null 降级交 LLM，但并列最长映射到<b>同一路径</b>时不算歧义，
     * 直接返回该路径。
     */
    public String resolveContains(String normalizedLabel) {
        if (!StringUtils.hasText(normalizedLabel)) {
            return null;
        }
        Map<String, String> aliases = approvedAliases;
        String lowerLabel = normalizedLabel.toLowerCase();
        int bestLen = 0;
        String bestPath = null;
        boolean ambiguous = false;
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String key = entry.getKey();
            if (key.length() < 2 || !lowerLabel.contains(key.toLowerCase())) {
                continue;
            }
            if (key.length() > bestLen) {
                bestLen = key.length();
                bestPath = entry.getValue();
                ambiguous = false;
            } else if (key.length() == bestLen && !entry.getValue().equals(bestPath)) {
                // 并列最长且映射到不同路径 → 歧义
                ambiguous = true;
            }
        }
        if (ambiguous || bestPath == null) {
            return null;
        }
        return bestPath;
    }
}
