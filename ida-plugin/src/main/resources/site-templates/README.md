# 站点模板库（site-templates）

本目录存放「网申站点模板」JSON 文件，由 `SiteTemplateRegistry` 在应用启动时自动加载（`classpath:site-templates/*.json`）。
命中模板的站点可在确定性匹配与 LLM 之前**金标直填**（毫秒级、~100% 准确），是覆盖率从 80% 提升到 95%+ 的关键。

## 何时加模板

当反馈数据显示某 ATS/网申系统频繁命中 LLM、且字段映射稳定可预测时，就该为它建一个模板。
典型信号：同一站点 selector/锚点结构稳定、字段名规范、反复出现。

## 文件命名

- 以 `_` 开头的文件（如 `_template.json`、`_schema.json`）会被加载器跳过，可作为骨架/Schema 存放。
- 正式模板文件名建议 `{siteId}.json`，如 `beisen.json`、`northsen.json`。

## 字段说明

### 顶层

| 字段 | 必填 | 说明 |
|---|---|---|
| `siteId` | 是 | 唯一标识，用于日志、反馈记录、缓存诊断 |
| `displayName` | 否 | 展示名，仅日志 |
| `priority` | 否 | 多模板命中时排序，数字越小优先级越高，默认 100 |
| `fingerprints` | 是 | 指纹规则列表，OR 关系，命中任一即匹配本模板 |
| `fieldMappings` | 是 | 字段金标映射列表 |

### fingerprints 元素

各字段为「与」关系；至少一项非空才有意义。

| 字段 | 说明 |
|---|---|
| `urlHostSuffix` | pageUrl 的 host 后缀，多个用 `;` 分隔，如 `beisen.com;yingjiesheng.com` |
| `urlPathRegex` | pageUrl 路径正则（忽略大小写），如 `.*/campus/apply/.*` |
| `fieldAnchorHint` | 字段锚点特征，复用 `FieldAnchor` 结构，用于 URL 不唯一时二次确认 |
| `minFieldAnchorHits` | 满足 `fieldAnchorHint` 的最少字段数，默认 1 |

### fieldMappings 元素

| 字段 | 必填 | 说明 |
|---|---|---|
| `anchor` | 是 | 锚点条件，复用 `FieldAnchor`，非空条件为「或」关系 |
| `resumePath` | 否 | 简历字段路径，复用 `ResumeFieldAccessor` 格式，如 `basic.fullName`、`education.0.schoolName` |
| `enumCategory` | 否 | 下拉枚举类别：`gender`/`degree`/`politicalStatus`/`hukouType`，交给 `OptionMatcher` |
| `targetRole` | 否 | 复合字段角色（本轮默认不启用，留扩展） |
| `skip` | 否 | true 表示该字段无法自动填（附件/验证码等），直接进 blockedFields |

### FieldAnchor 条件（「或」关系）

| 字段 | 匹配方式 |
|---|---|
| `clientFieldKeyEquals` | 精确匹配 `clientFieldKey` |
| `dataFormFieldIdEquals` | 精确匹配 `dataFormFieldId` |
| `dataFormFieldIdPrefix` | 前缀匹配 `dataFormFieldId`（ATS 动态 id 场景） |
| `i18nLabelEquals` | 精确匹配 `i18nLabel` |
| `labelTextEquals` | 标签归一化后精确匹配（去冒号/必填/请输入等） |
| `selectorContains` | `selector` 包含子串 |

## 处理边界（模板层不做的事）

- **复合字段**（`compoundSegment` 非空，如区号+号码）→ 交 LLM。
- **长文本/textarea**（需多字段拼接）→ 交 LLM。
- **已有预填值**的字段 → 不覆盖，交下层。
- **下拉选项匹配失败** → 降级交 LLM（不瞎填）。

模板产出的 assignment 仍会经过 `sanitizePlan` 的 selector 白名单与锚点合并，安全网不变。

## 验证

新增模板后建议跑 `SiteTemplateRegistryTest` 与 `SiteTemplateMatcherTest`，并在 Apifox 用真实站点快照回归。

## 注意

`beisen.json` 为**示例模板**，字段映射基于公开特征的最佳估计，**需用真实北森页面快照校准后**再用于生产。
金标映射错误会导致高置信误填，上线前务必人工 review 每条 `fieldMappings`。
