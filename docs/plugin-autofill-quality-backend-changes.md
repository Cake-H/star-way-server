# 一键填表体验增强 — 后端改动说明

本文档对应「按问题顺序」落地的**主方案**中与后端相关的实现，便于 Code Review 与联调。

## 1. 云简历模块条数（问题 1）

### 新增类型（`ida-common`）

| 类型 | 说明 |
|------|------|
| `PluginResumeModuleCountsVO` | 教育/工作/项目等 9 类可重复模块的 `*Count` 字段，与 `ResumeParseDraftDto` 各 `List` 长度一致。 |
| `PluginAutofillResumeContextVO` | 封装 `resumeId`、`schemaVersion`、`updatedAt`、`moduleCounts`，作为插件 GET 接口响应体。 |
| `ResumeDraftModuleCountsExtractor` | 从 `ResumeParseDraftDto` 纯函数统计条数，无 IO；**填表提示词 PART_C 与 HTTP 接口共用**，避免两处数字漂移。 |

### 应用层（`ida-app`）

- `AppResumeQueryService#getAutofillResumeContext(userId, resumeId)`：内部调用既有 `getResumeDetail`（鉴权与 draft 装配不变），再执行 `ResumeDraftModuleCountsExtractor.fromDraft(detail.getDraft())`，**不增加额外 SQL**。

### 插件 API（`ida-plugin` / `PluginResumeController`）

- `GET /api/v1/plugin/resume/{resumeId}/autofill-context`  
  返回 `PluginAutofillResumeContextVO`，供扩展在**采页面快照之前**按云端真实条数点击「+添加」展开模块。

### 方舟提示词 PART_C（`PluginFormAutofillAiService` + `PluginAutofillPrompts`）

- 在原有 PART_A（简历 JSON）、PART_B（页面控件 JSON）之后，追加 **PART_C**（`moduleCountsJson`，与 `PluginResumeModuleCountsVO` JSON 形态一致）。
- 系统提示词中增加规则：模型可根据 PART_C 与 PART_B 控件数量差异，在 `hint` 中提示用户先展开对应块；**禁止虚构 selector**。

---

## 2. 复合字段误填（问题 2）

### DTO 扩展（`PluginPageFormFieldDto`）

| 字段 | 用途 |
|------|------|
| `compoundSegment` | 标识本 DOM 在复合行中的语义（如 `dial_code`、`local_number`、`id_type`、`id_number`、`date_start`、`date_end`），与 `subAssignments[].targetRole` 对齐。 |
| `interactionMode` | 扩展推断的交互类型（如 `composite_left_select`、`combobox_typeahead`），供模型区分「只选选项」与「填长文本」。 |
| `optionLabelsSample` | 下拉/联想可见选项文案采样（≤40 条，`@Size` 校验），避免模型凭空造选项。 |

### 提示词（`PluginAutofillPrompts.SYSTEM`）

- 明确 **禁止** 把完整手机号/证件号写入区号/类型对应的 selector；`dial_code`/`id_type` 仅填与页面选项一致的短文案。
- 要求复合字段**必须**走 `subAssignments`，并与 `compoundSegment` / `interactionMode` 一致。

### 清洗层（`PluginFormAutofillAiService#normalizeTargetRole`）

- 合法 `targetRole` 增加 `date_start`、`date_end`，与「起止时间」两格控件一致。

---

## 3. 需「先输入再选」的控件（问题 3）

- 以后端可落地部分为主：**`optionLabelsSample` + `interactionMode=combobox_typeahead` + 提示词第 12 条**（有采样则匹配项；无法匹配则 `blockedFields` 并说明）。
- 实际键盘模拟、聚焦、触发联想列表仍依赖**扩展执行层**（见前端适配文档）。

---

## 4. 长文本/项目描述被摘要（问题 4）

- 在 `SYSTEM` 中删除与「长描述」矛盾的旧规则（如强制每条 value≤120 字、鼓励摘关键词等表述）。
- 新增规则 **第 9 条**：textarea 或单一大段描述对应简历多列（职责/成果/描述）时，必须**拼接完整可溯源原文**，禁止模型自行摘要或重写。

---

## 5. 模型「训练」（问题 5）

- **未在本仓库接入微调流水线**；工程上可在火山方舟等平台使用「提示词 + 评测集 + 人工抽检」迭代。
- 产品化路线建议：沉淀**站点模板 / 字段对照金标**，用于离线评测与后续 SFT/DPO（本迭代仅文档层建议，无代码变更）。

---

## 依赖与兼容性

- 旧版扩展**不传**新字段时行为与以前兼容：`compoundSegment` 等为可选；PART_C 由服务端从简历自动注入，**不依赖扩展传参**。
- 填表请求体体积可能因 `optionLabelsSample` 略增，仍受单页字段上限 `MAX_FIELDS` 约束。

## 变更文件清单（核心）

- `ida-common`：`PluginResumeModuleCountsVO`、`PluginAutofillResumeContextVO`、`ResumeDraftModuleCountsExtractor`、`PluginPageFormFieldDto`（扩展字段）、`PluginSubAssignmentDto`（文档补充）。
- `ida-app`：`AppResumeQueryService`、`AppResumeQueryServiceImpl`。
- `ida-plugin`：`PluginAutofillPrompts`、`PluginFormAutofillAiService`、`PluginResumeController`。
