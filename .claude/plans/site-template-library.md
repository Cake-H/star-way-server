# 后端站点模板库（Site Template Library）实现方案

## 一、目标与定位

在现有「确定性匹配 → LLM」两段降级链**之前**，插入第三段「站点模板」层，形成业界标准的三段降级链：

```
站点模板（金标直填，毫秒级，~100% 准确）
   └─未命中→ 确定性规则（clientFieldKey + 标签启发式）
                └─未命中→ LLM（语义兜底）
```

**为什么这是覆盖率从 80% → 95%+ 的最大杠杆**：Simplify 靠 100+ ATS 模板库做到业界最高覆盖；北森/大汉/牛客等标准化 ATS 的表单结构高度稳定，模板命中即可毫秒级 100% 准确直填，不必每次靠 LLM 猜、也不受 LLM 波动影响。

## 二、核心设计

### 2.1 站点指纹识别

前端只发 `pageUrl + pageTitle + fields[]`（含 selector/labelText/dataFormFieldId/i18nLabel/clientFieldKey/name/id），不发完整 DOM。因此指纹基于两类信号：

- **主信号 — URL 模式**：`pageUrl` 的 host 后缀 / 路径正则。如 `*.beisen.com`、`campus.{corp}.cn`、`yingjiesheng.com`。
- **辅信号 — 字段锚点特征**：dataFormFieldId 前缀规律、i18nLabel 命名空间、selector class 特征（如 `.ats-` 前缀）。用于 URL 不唯一时的二次确认。

`SiteTemplateRegistry.match(snapshot)` 遍历已注册模板的指纹规则，第一个命中者胜出；无命中则返回 `Optional.empty()`，跳过模板层走原确定性匹配。

### 2.2 模板数据结构（配置化，JSON 驱动）

模板定义放 `ida-plugin/src/main/resources/site-templates/*.json`，启动时加载到 `SiteTemplateRegistry`。运营/数据团队可不改代码加模板。复用现有 `ResumeFieldAccessor` 的字段路径格式与 `OptionMatcher` 的枚举匹配。

```java
// ida-common/.../autofill/site/SiteTemplate.java
public record SiteTemplate(
    String siteId,            // "beisen_campus"
    String displayName,       // "北森校招"
    int priority,             // 多模板命中时的排序
    List<SiteFingerprint> fingerprints,   // 识别规则（OR 关系）
    List<TemplateFieldMapping> fieldMappings
) {}

// 锚点条件：满足任一非空条件即视为命中本映射行
public record FieldAnchor(
    String clientFieldKeyEquals,    // 精确匹配 field.clientFieldKey
    String dataFormFieldIdEquals,   // 精确匹配
    String dataFormFieldIdPrefix,   // 前缀匹配
    String i18nLabelEquals,         // 精确匹配
    String labelTextEquals,         // 精确匹配（归一化后）
    String selectorContains         // selector 包含子串
) {}

public record TemplateFieldMapping(
    FieldAnchor anchor,
    String resumePath,         // 复用 ResumeFieldAccessor 路径，如 "basic.fullName"、"education.0.schoolName"
    String enumCategory,       // 可选：下拉枚举类别（gender/degree/...），交给 OptionMatcher
    String targetRole,         // 可选：复合字段角色（dial_code/local_number/...）
    boolean skip               // 可选：模板已知该字段无法自动填（如附件），直接进 blocked
) {}
```

### 2.3 执行流程（修改 `PluginFormAutofillAiService.planAutofill`）

在现有「阶段 3 确定性匹配」前插入「阶段 2.5 站点模板匹配」：

```
阶段 2.5（新）：站点模板匹配
  templateOpt = siteTemplateRegistry.match(req.getPage())
  if 命中:
    templateResult = siteTemplateMatcher.apply(template, fields, draft)
       - 命中金标的普通字段 → assignments (confidence=0.99, matchType="template")
       - 下拉字段 → 复用 OptionMatcher；选项匹配失败 → 该字段降级进 unmatched（交 LLM）
       - 复合字段（compoundSegment 非空）/长文本（textarea/已知长文本路径）→ 模板层不处理，进 unmatched
       - skip=true 的字段 → 直接进 blockedFields (reason=other, userHint="模板标注需人工")
       - push partialResult（模板已匹配部分）
  else:
    templateResult = empty
阶段 3：确定性匹配（仅处理 templateResult.unmatchedFields）
阶段 5：LLM（仅处理确定性仍 unmatched 的字段）
```

合并优先级：**模板 > 确定性 > LLM**。`mergeResults` 从两层扩为三层，order 重新编号（模板字段在前）。

### 2.4 模块归属

| 模块 | 新增/修改 | 内容 |
|---|---|---|
| `ida-common` | 新增 | `site/SiteTemplate.java`、`SiteFingerprint.java`、`FieldAnchor.java`、`TemplateFieldMapping.java`、`SiteTemplateMatchResult.java`（可序列化，两端复用） |
| `ida-plugin` | 新增 | `plugin/site/SiteTemplateRegistry.java`（加载 JSON + 指纹匹配）、`SiteTemplateMatcher.java`（执行匹配）、`resources/site-templates/*.json` |
| `ida-plugin` | 修改 | `PluginFormAutofillAiService.java`：插入模板阶段 + 三层 merge |
| `ida-plugin` | 修改（可选） | `PluginAutofillFeedbackService`：反馈记录里加 `siteId` 字段，为未来从反馈数据反向沉淀模板留口子 |

### 2.5 与现有代码的复用关系

- **字段值提取**：复用 `ResumeFieldAccessor.extractValue(draft, resumePath)`，模板的 `resumePath` 直接用其格式。
- **下拉选项匹配**：复用 `OptionMatcher.matchOption(rawValue, optionLabelsSample, enumCategory)` 与 `ChineseFieldAliasTable.inferEnumCategory`。
- **复合字段/长文本判定**：复用 `DeterministicFieldMatcher` 中已有的 `isLongTextField` 逻辑（抽到 `SiteTemplateMatcher` 或公共工具）。
- **sanitize**：模板产出的 assignment 仍走 `sanitizePlan` 的 selector 白名单与锚点合并，保证安全网不变。
- **缓存**：模板命中结果同样写 `resultCacheService`，key 不变。

## 三、本轮落地范围（务实，不硬编）

我没有真实站点数据，硬编模板字段映射会不准。因此本轮：

1. **搭好框架**：全部数据结构 + Registry + Matcher + JSON 加载 + 三层 merge 串联。
2. **提供 1 个示例模板** `site-templates/beisen.json`（北森校招，基于公开 DOM 特征的最佳估计，标注为「示例，需用真实数据校准」），用于验证框架可跑通。
3. **提供 1 个空模板骨架** `site-templates/_template.json` + `site-templates/README.md`，说明运营如何加模板。
4. **单测**：`SiteTemplateRegistryTest`（指纹识别命中/未命中）、`SiteTemplateMatcherTest`（普通字段命中、下拉匹配成功、下拉失败降级、复合字段跳过、skip 进 blocked）。
5. **反馈留口子**：`PluginAutofillFeedbackService` 记录里加 `siteId`（若命中），为后续「从反馈数据自动沉淀模板」铺路（本轮不实现沉淀逻辑）。

**真实模板内容由反馈数据驱动持续扩充**——这是后续迭代的事，不在本轮。

## 四、新增文件清单

```
ida-common/src/main/java/com/smartapply/ida/common/domain/plugin/autofill/site/
  SiteTemplate.java
  SiteFingerprint.java
  FieldAnchor.java
  TemplateFieldMapping.java
  SiteTemplateMatchResult.java

ida-plugin/src/main/java/com/smartapply/ida/ai/plugin/site/
  SiteTemplateRegistry.java
  SiteTemplateMatcher.java

ida-plugin/src/main/resources/site-templates/
  beisen.json          (示例模板，标注需校准)
  _template.json       (空骨架)
  README.md            (运营加模板指引)

ida-plugin/src/test/java/com/smartapply/ida/ai/plugin/site/
  SiteTemplateRegistryTest.java
  SiteTemplateMatcherTest.java
```

## 五、修改文件清单

```
ida-plugin/.../PluginFormAutofillAiService.java
  - 注入 SiteTemplateRegistry + SiteTemplateMatcher
  - 阶段 2.5 插入模板匹配
  - mergeResults 扩为三层（模板 > 确定性 > LLM）
  - 日志加模板命中情况

ida-plugin/.../PluginAutofillFeedbackService.java (可选)
  - 反馈记录加 siteId 字段
```

## 六、不做的事（明确边界）

- 不动前端代码（前端在另一仓库，本轮只出后端契约）。
- 不接入微调（沿用提示词 + 评测迭代路线）。
- 不在本轮批量编造站点模板内容（避免无数据硬编导致错误金标）。
- 不改 `DeterministicFieldMatcher` 和 `PluginAutofillPromptProvider` 的核心逻辑（模板层与它们并列，不侵入）。

## 七、验收标准

1. 无模板命中时，行为与现状**完全一致**（回归零风险）。
2. 命中模板时，金标字段以 `matchType="template"`、`confidence=0.99` 直填，不进入 LLM。
3. 模板下拉字段选项匹配失败时，自动降级交 LLM，不报错不漏字段。
4. 复合字段、长文本字段不被模板层误处理，正确流转到 LLM。
5. 单测全绿；现有 autofill 相关单测（如有）不回归。

## 八、后续迭代（不在本轮，记录备忘）

- 反馈数据消费入库 + 字段级命中率统计（P0，独立任务）。
- 从反馈数据自动沉淀/校准模板（依赖上一条）。
- 扩充 `ChineseFieldAliasTable` 同义词库（独立小任务）。
- 提示词加 siteId 上下文 + precision>coverage 约束（独立小任务）。
- 站点指纹增强：DOM 特征指纹（需前端在快照里加 `siteFingerprint` 字段，跨仓库协作）。
