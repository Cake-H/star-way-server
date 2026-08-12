# 插件一键填入 M2 优化说明

本文档记录「插件简历一键填入」M2 迭代（A+D 工作流）的后端改动：反馈闭环半自动回灌（含 admin 全局统计）+ 确定性匹配强化。

## 1. 动态别名表（DB 驱动，审核后生效）

- 新表 `ida_plugin_autofill_alias`（脚本 `database/mysql/ida_v9_plugin_autofill_alias.sql`）：
  `alias_label → field_path`，带 `source`（feedback/manual）与 `status`（pending/approved/rejected），
  `uk_alias_label` 唯一约束防重复登记。
- 实体 `PluginAutofillAlias`（ida-common），Mapper `PluginAutofillAliasMapper`（ida-app，
  ida-plugin 与 ida-admin 共用，admin 进程通过 `@MapperScan` 全包扫描装配，无需重复建设）。
- `DynamicFieldAliasService`（ida-plugin `ai.plugin.deterministic`）：
  - 启动时 + 每 5 分钟（`ida.plugin.autofill.alias.refresh-interval-ms`，默认 300000）全量刷新 `approved` 行到内存；
  - 加载时用 `ResumeFieldAccessor.isKnownKey` 校验 fieldPath，非法路径丢弃并告警；
  - 刷新异常静默降级（保留旧缓存），不影响匹配主流程；
  - `resolveExact` 精确匹配；`resolveContains` 最长包含匹配（key 长度 ≥ 2，并列最长返回 null）。

## 2. 确定性匹配强化（D 工作流）

- `DeterministicFieldMatcher.resolveByLabels` 查找顺序升级为四级：
  **静态精确 → 静态 contains → 动态精确 → 动态 contains**。
  动态别名命中的 assignment 使用 `confidence=0.80`、`matchType="dynamic_alias"`，
  静态命中维持 `0.85` / `"deterministic"`，便于反馈统计区分来源。
- `ChineseFieldAliasTable` 新增 `resolveByLabelContains`（与动态服务同规则的最长包含匹配），
  并扩充约 20 条常见静态别名（个人邮箱/QQ邮箱/家庭住址/联系地址/期望工作地点/当前职位/户口所在地/
  入学年份/毕业年份/毕业院校/所学专业/工作单位/期望月薪 等，路径均经 `ResumeFieldAccessor` 核实）。
- `OptionMatcher` 层级调整：**归一化精确 → 枚举别名（提前，更可靠）→ contains 打分竞争**。
  contains 阶段对所有双向包含候选打分（较短串/较长串），取最高分；**并列最高返回 null**（歧义降级 LLM），
  不再盲目取第一个候选。
- 删除死代码 `PluginAutofillPrompts`（提示词唯一来源为 ida-ai 的 `PluginAutofillPromptProvider`）。

## 3. 反馈值域校验

`PluginAutofillFeedbackService` 入口对每条 `FieldFeedbackItem` 做白名单校验：

- `status` 仅允许 `filled/skipped/error`，非法值整行跳过（debug 日志）；
- `failReason` 仅允许 `option_not_found/readonly/date_nav_failed/selector_miss/other`，非法值归一为 `other`。

防止异常前端/旧版本扩展污染统计口径。

## 4. admin 全局统计与候选挖掘

新端点（前缀 `/api/v1/admin/plugin`，与其他后台接口一致走 `AdminBearerTokenAuthenticationFilter` Bearer 鉴权）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/autofill-feedback/stats?days=&siteHost=` | 全局聚合统计（不带 userId 过滤），结构复用 `PluginAutofillFeedbackStatVO` |
| GET | `/autofill-feedback/alias-candidates?days=&limit=` | 近 N 天 `option_not_found/selector_miss` 按 clientFieldKey 聚合 Top N（key+count+样例 siteHost） |
| GET | `/autofill-feedback/site-candidates?days=&limit=` | 按 siteHost 聚合 total/filled/fillRate，命中率升序（最差在前）Top N |
| GET | `/autofill-alias?status=&page=&size=` | 动态别名分页列表 |
| POST | `/autofill-alias` | 新增别名（status 默认 approved 即生效） |
| PUT | `/autofill-alias/{id}/status` | 审核别名（body: `{"status":"approved"｜"rejected"}`） |

实现：`AdminPluginAutofillController` + `AdminPluginAutofillService(Impl)`（ida-admin `module.admin.plugin`），
聚合 SQL 参照 `PluginAutofillFeedbackStatsService` 的 `QueryWrapper.selectMaps + groupBy` 写法，去掉 `user_id` 条件。

## 5. 配套既有改动（本轮之前的工作流）

- 结果缓存修复：`PluginCacheConstants`（ida-common）统一简历预加载缓存键，简历写路径主动失效；
- 站点模板加载校验：`TemplateFieldMapping`/`SiteTemplateRegistry` 校验 resumePath 与 enumCategory 合法性；
- PART_A 裁剪：`ResumeDraftPromptPruner` 按页面字段相关性裁剪简历 JSON，降低 token 消耗；
- `PageUrlUtils.extractPath`、枚举别名补 `idDocType`。

## 6. 验证

```bash
mvn -q -pl ida-common,ida-app,ida-plugin,ida-admin -am test -Dsurefire.failIfNoSpecifiedTests=false
```

新增单测：`OptionMatcherTest`、`ChineseFieldAliasTableTest`、`DeterministicFieldMatcherTest`、
`DynamicFieldAliasServiceTest`（ida-plugin，纯单元无 Spring 上下文）、
`AdminPluginAutofillServiceImplTest`（ida-admin，Mockito 风格同 `AdminApplySiteServiceImplTest`）。
