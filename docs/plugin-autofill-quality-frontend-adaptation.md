# 一键填表体验增强 — 扩展端适配说明

> ⚠️ **本文档部分内容为旧版约定**（API 摘要缺反馈接口等）。**最新权威对接说明见 [`plugin-autofill-frontend-integration-current.md`](./plugin-autofill-frontend-integration-current.md)**，冲突处以该文为准。

格式：**问题简述 → 后端已做 → 扩展需做**。

---

## 1、未展开的动态模块导致快照缺字段，模型无法映射

**后端已做：** 提供 `GET /api/v1/plugin/resume/{resumeId}/autofill-context`，返回 `moduleCounts`（教育/工作/项目等条数，与云端简历一致）；填表接口在方舟消息中自动附带 **PART_C**（同数据源），模型可在 `hint` 中提示块数不足。

**前端需做：**  
- 在用户确认「一键填表」后、采快照**之前**，先请求 `autofill-context`，按 `projectCount`、`workCount` 等与当前页「+添加」行为对齐，自动点击展开对应数量的块（允许比 PART_C **多**展开，但不宜**少**）。  
- 将本地缓存猜测改为：**以接口返回为准**，`updatedAt`/`schemaVersion` 变化时重新拉取。  
- 若页面结构无法在自动化中可靠展开，可降级为提示用户手动展开后再采快照。

---

## 2、复合字段（手机区号+号码、证件类型+号码）误填到左侧下拉

**后端已做：** `PluginPageFormFieldDto` 增加 `compoundSegment`、`interactionMode`；提示词强制区号/类型与号码分 `subAssignments`，并禁止把长数字写进下拉对应 selector；`targetRole` 支持 `date_start`/`date_end`。

**前端需做：**  
- 采集快照时为**同一视觉行**内的每个可编辑子控件生成独立 `selector`，并设置 `compoundSegment`（如左：`dial_code`/`id_type`，右：`local_number`/`id_number`）。  
- 对左侧 `select` 与右侧 `input` 分别建 `PluginPageFormFieldDto` 行（或一条主行 + 由扩展在执行阶段按 `subAssignments` 写入子 selector——与现有协议一致）。  
- 执行 `assignments` 时：按 `targetRole` 将值写入对应子节点，**禁止**用主 `value` 覆盖整行容器。

---

## 3、学校等「先输入再联想选择」的控件无法自动选

**后端已做：** 支持 `interactionMode`（建议值 `combobox_typeahead`）与 `optionLabelsSample`（可见选项文案列表）；提示词要求无法匹配选项时走 `blockedFields` 而非瞎填。

**前端需做：**  
- 在快照中标注 `interactionMode`；在**用户已打开下拉/联想列表**或脚本触发后，采集当前可见的 `optionLabelsSample`（不必全量，≤40 条）。  
- 执行填表时：对 typeahead 需模拟 **focus → 输入关键词 → 等待列表 → 点击选项**（纯 `input.value=` 往往无效），该部分以扩展自动化能力为主。

---

## 4、项目描述等长文本被模型摘要，而非拼接简历多字段

**后端已做：** 系统提示词第 9 条要求长文本/textarea **拼接简历原文**（职责+成果+描述等），禁止随意摘要；删除与「限 120 字」冲突的旧约束。

**前端需做：**  
- 无需改协议即可受益；若仍不满意，可在扩展测增加「填充后 diff 简历原文」的抽检与上报。  
- 继续保证 `resumeId` 对应 draft 为最新（保存后再填表）。

---

## 5、模型适配多站点、多字段名的泛化能力

**后端已做：** 本轮以提示词与结构化上下文（PART_C、控件元数据）为主；未接入私有化微调。

**前端需做：**  
- 收集失败样例（页面快照 + 返回 assignments + 用户修正）供后续迭代提示词或评测集。 
- 若使用方舟等平台的微调，需准备脱敏后的「快照 + 期望 JSON」对。

---

## API 摘要

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/plugin/resume/{resumeId}/autofill-context` | 取模块条数与版本信息 |
| POST | `/api/v1/plugin/resume/autofill-plan/tasks` | 异步填表（消息内含 PART_C） |
