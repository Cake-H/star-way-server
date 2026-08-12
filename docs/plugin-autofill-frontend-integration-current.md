# 浏览器插件「简历一键填入」前端对接说明（当前版本权威版）

> 本文档是「浏览器插件端简历一键填入」功能**当前版本的唯一权威对接说明**。
> 文中每个字段名、枚举值、行为描述均已对照后端代码逐一核实（主要来源：
> `ida-plugin/.../resume/controller/PluginResumeController.java`、
> `ida-plugin/.../ai/plugin/PluginFormAutofillAiService.java`、
> `ida-plugin/.../ai/plugin/deterministic/DeterministicFieldMatcher.java`、
> `ida-plugin/.../module/plugin/resume/autofill/*`、
> `ida-common/.../domain/plugin/autofill/*`、`ida-ai/.../prompt/PluginAutofillPromptProvider.java`）。
> 如本文与任何旧文档冲突，**以本文为准**；本文与代码冲突时，以代码为准并反馈后端修订本文。

---

## 1. 版本说明

- 本文对应的后端版本：**M2 迭代之后**（即已包含：三层降级链、异步任务 + `partialResult` 增量推送、动态别名表、反馈值域白名单校验、结果缓存、简历预加载）。
- 与旧文档的关系：
  - `docs/plugin-autofill-async-frontend-integration.md` —— 异步轮询的**基础约定仍有效**，但它写于 `partialResult` 之前，其「轮询过程中没有填表列表」「assignments 只在 SUCCEEDED 的 result 里」两条结论**已过时**；`phase` 枚举示例也漏了 `DETERMINISTIC_MATCHING`。这些内容以本文 §6/§8 为准。
  - `docs/plugin-autofill-quality-frontend-adaptation.md` —— 采集侧的 `compoundSegment` / `interactionMode` / `optionLabelsSample` 约定**仍有效**，已被本文 §5 吸收并补全；其 API 摘要缺反馈接口，以本文 §4 为准。
  - `docs/plugin-autofill-m2-optimization.md` —— 是**后端改动记录**（动态别名、反馈白名单、admin 统计），不是前端对接文档；其中「反馈值域校验」一节与前端直接相关的结论已并入本文 §6.5/§10。
- 上述旧文档保留作历史参考，不再更新；前端适配检查一律对照本文 §11 清单。

---

## 2. 整体逻辑路线

服务端生成填表计划走的是**三层降级链**，配合**异步任务**与**增量推送**，最后是**反馈闭环**：

```
扩展(content script)                     插件服务端(ida-plugin)
        │                                      │
        │ ① GET autofill-context               │  返回简历各模块条目数(moduleCounts)，
        │─────────────────────────────────────▶│  并顺带把简历预热进 Redis(TTL 10min)
        │                                      │
   按 moduleCounts 点击「+添加」展开块，
   然后采集页面快照(fields[])
        │                                      │
        │ ② POST autofill-plan/tasks           │  注册 taskId(PENDING)，投递线程池，
        │─────────────────────────────────────▶│  立即返回 taskId
        │                                      │
        │ ③ GET tasks/{taskId} 轮询            │  后台线程依次执行：
        │◀─────────────────────────────────────│   第1层 站点模板匹配(命中ATS模板→金标直填)
        │   RUNNING + partialResult(模板层)     │      → 推送 partialResult(percent≈18)
        │   RUNNING + partialResult(模板+确定性)│   第2层 确定性匹配(clientFieldKey直映射
        │                                      │      +标签别名启发式) → 再推 partialResult(≈26)
        │   （可立即开始增量填入）               │   第3层 LLM：只处理仍未匹配的字段
        │                                      │      → 清洗(selector白名单/锚点合并/枚举归一)
        │   SUCCEEDED + result(三层合并全量)    │      → 按 模板>确定性>LLM 合并 → 写缓存(2h)
        │                                      │
   按 result.assignments 执行 DOM 填入，
   blockedFields 标红交人工
        │                                      │
        │ ④ POST autofill-plan/feedback        │  白名单校验后异步入 Redis 队列，
        │─────────────────────────────────────▶│  消费器批量入库(脱敏+截断)，供统计迭代
```

要点：

- **三层降级链**：站点模板（毫秒级、confidence=0.99、matchType=`template`）→ 确定性匹配（<1s，`deterministic`/`dynamic_alias`）→ LLM（只处理前两层剩下的字段，`exact`/`fuzzy`/`guess`）。任何一层抛异常都静默降级到下一层，不会整单失败。
- **异步任务**：提交接口立即返回 `taskId`，模型调用在专用线程池（core 4 / max 8 / 队列 200）执行，HTTP 不阻塞。
- **partialResult 增量推送**：模板层、确定性层一完成就把已匹配部分写入任务快照，前端在 `RUNNING` 期间即可开始填，不必等 LLM。
- **结果缓存**：同用户 + 同简历版本 + 同页面结构指纹命中缓存时**直接返回最终结果**（跳过三层，也不会有 partialResult），TTL 2 小时。
- **反馈闭环**：前端逐字段上报填写结果，服务端白名单校验后入库，用于命中率统计与别名表扩充。

---

## 3. 完整对接时序（前端逐步该做什么）

| 步骤 | 前端动作 | 不该做的事 |
|---|---|---|
| 1. 预取上下文 | 用户选定简历后、采快照**之前**，调 `GET {BASE}/api/v1/plugin/resume/{resumeId}/autofill-context`，按 `moduleCounts` 自动点击页面「+添加」展开对应数量的块（允许多展开、不宜少展开）；同时记住 `dateFieldPrecision` 供日期控件使用。该调用还会让服务端把简历预热进 Redis（TTL 10 分钟），后续填表更快 | 不要用本地缓存猜测块数；不要在采完快照后才调它（那时 DOM 已定型） |
| 2. 采集快照 | 扫描当前页可交互控件，按 §5 规范生成 `fields[]`（稳定 selector、锚点三件套、compoundSegment、interactionMode、optionLabelsSample） | 不要把 readonly/隐藏 submit 等明显不可写控件混进来当普通字段；不要漏 `clientFieldKey`（确定性匹配的主要输入） |
| 3. 提交任务 | `POST .../autofill-plan/tasks`，body 为 `{resumeId, page}`。**提交前禁用「一键填入」按钮**防连点。成功后保存 `taskId` | 不要在提交响应里找 assignments（提交响应只有 taskId）；同一页连点会产生多个 taskId |
| 4. 轮询 | `GET .../autofill-plan/tasks/{taskId}`，间隔建议 500ms～2s（低于 200ms 无意义）。`RUNNING` 且 `partialResult != null` 时可**立即开始增量填入**（见 §8） | 不要因 percent 长时间停在 CALLING_MODEL 区间（约 38%）判定卡死；不要在终态后继续轮询 |
| 5. 执行填入 | 终态 `SUCCEEDED` 后以 `result` 为准（它是全量合并结果，partial 期间已填的字段在 result 中值不变），按 §7 规范写入 DOM；`blockedFields` 标红交人工；`fillable=false` 只展示 `hint` | 不要用 partialResult 覆盖 result；不要对带 `subAssignments` 的复合行写主 `value` |
| 6. 上报反馈 | 每个字段执行完记录 `{selector, status, failReason?, actualValue?}`，整页完成后批量 `POST .../autofill-plan/feedback`（含 `taskId`、`siteId` 透传） | 不要上报白名单外的 status/failReason（会被丢弃/归一，见 §6.5）；不要在 actualValue 里塞无关 PII |
| 7. 清理 | 终态（SUCCEEDED/FAILED/轮询报错）后清定时器、恢复按钮、丢弃 taskId | taskId 30 分钟无更新即被服务端清除，不要长期持有复用 |

---

## 4. 接口契约

统一前缀：`{BASE}/api/v1/plugin/resume`。

### 4.0 鉴权与统一响应壳

- **鉴权**：请求头 `Authorization: Bearer <token>`（与插件其他接口同一套登录态）。未登录或 token 无效：HTTP 状态码 **401**，body 为统一响应壳 `{"code":30007,"level":"user","ok":false,"msg":"未登录或登录已失效，请重新登录","data":null}`。
- **统一响应壳**（`ResponseDTO`）：

```json
{ "code": 0, "level": null, "ok": true, "msg": "操作成功", "data": { }, "dataType": 0, "success": true, "message": "操作成功" }
```

  - 成功：`code === 0` 且 `ok === true`，业务数据在 `data`。
  - 业务失败：HTTP 200，`code !== 0`，读 `msg`，`data` 为 null。
  - `success`/`message` 是 `ok`/`msg` 的序列化别名（axios 兼容），前端任选一组判断即可，不要两组混用。
- **常见错误码**（`code` 值以后端 `UserErrorCode`/`SystemErrorCode` 为准）：

| code | 含义 | 典型触发 |
|---|---|---|
| 0 | 成功 | — |
| 30001 | 参数错误 | `@Valid` 校验失败：`resumeId`/`page`/`fields` 为 null、`selector` 空白、`optionLabelsSample` 超 40 条等；`msg` 为拼接后的具体文案 |
| 30007 | 未登录或登录已失效 | 无/无效 Bearer token（HTTP 401） |
| 30100 | 操作无法完成（业务异常默认码） | 「任务不存在、已过期或无权访问」「简历内容为空，无法生成填表计划」「调用大模型失败：…」「请求体不完整」等，文案在 `msg` |
| 30200 | AI 额度不足 | 计费服务拒绝（前端应引导充值/兑换） |
| 10001 | 系统繁忙 | 未捕获异常兜底（如线程池队列满拒绝提交） |

### 4.1 `GET /{resumeId}/autofill-context` —— 填表前置上下文

无请求体。响应 `data`（`PluginAutofillResumeContextVO`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `resumeId` | number | 与路径一致 |
| `schemaVersion` | number | 简历主表版本号，可做本地缓存失效判断 |
| `updatedAt` | string(LocalDateTime) | 简历最近更新时间，可与本地缓存比对 |
| `moduleCounts` | object | 各可重复模块在**云端**的真实条数，键：`educationCount`/`workCount`/`projectCount`/`campusExperienceCount`/`skillCount`/`languageCount`/`certificateCount`/`awardCount`/`publicationCount`（均为 int，默认 0） |
| `dateFieldPrecision` | map<string,string> | 日期精度提示：key 为 clientFieldKey 模式，value 为 `"date"`（完整日期）或 `"year_month"`（仅年月）。例：`{"education.*.enrollDate":"year_month","basic.birthDate":"date"}`。据此决定日期选择器操作方式 |

示例响应 `data`：

```json
{
  "resumeId": 1001,
  "schemaVersion": 3,
  "updatedAt": "2026-07-30T12:00:00",
  "moduleCounts": { "educationCount": 2, "workCount": 3, "projectCount": 1, "campusExperienceCount": 0, "skillCount": 4, "languageCount": 2, "certificateCount": 1, "awardCount": 0, "publicationCount": 0 },
  "dateFieldPrecision": { "education.*.enrollDate": "year_month", "basic.birthDate": "date" }
}
```

副作用：服务端会顺带把该简历预热进 Redis（TTL 10 分钟），预热失败不影响本接口返回。

### 4.2 `POST /autofill-plan/tasks` —— 提交异步填表任务（**推荐入口**）

请求体 `Content-Type: application/json`（`PluginAutofillPlanRequest`）：

| 字段 | 必填 | 说明 |
|---|---|---|
| `resumeId` | 是 | 简历 id；为 null 报 30001「resumeId 不能为空」 |
| `page` | 是 | 页面快照对象；为 null 报 30001「page 快照不能为空」 |
| `page.pageUrl` | 否 | 当前页 URL（参与结果缓存指纹的 host+path 部分） |
| `page.pageTitle` | 否 | `document.title` |
| `page.fields` | 是（不能为 null，可为空数组） | 控件快照数组，元素见 §5；为 null 报 30001「fields 不能为 null」；**空数组走短路径**：不调模型，直接返回 `fillable=false` 的提示 |

成功响应 `data`（`PluginAutofillTaskSubmitResultVO`）：

```json
{ "taskId": "3f6c2f70-....", "pollPathTemplate": "/api/v1/plugin/resume/autofill-plan/tasks/{taskId}" }
```

- `taskId`：UUID，轮询必存。
- `pollPathTemplate`：路径提示；建议直接拼 `${BASE}/api/v1/plugin/resume/autofill-plan/tasks/${taskId}`，不要解析模板。

注意：该接口**只注册任务**，参数业务校验（简历是否存在等）发生在后台线程——校验失败会体现为轮询时 `status=FAILED` + `errorMessage`，而不是提交时同步报错。

### 4.3 `GET /autofill-plan/tasks/{taskId}` —— 轮询任务状态

响应 `data`（`PluginAutofillTaskStatusVO`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | string | 与提交一致 |
| `status` | string | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED`（终态为后两者） |
| `phase` | string | 细阶段枚举名，见 §6.4 |
| `message` | string | 给用户看的短句（中文），可展示 |
| `percent` | number | 0~100 近似进度；`FAILED` 时也会置 100 |
| `createdAtEpochMs` / `updatedAtEpochMs` | number | 毫秒时间戳；`updatedAtEpochMs` 只在**服务端推进度**时刷新，轮询读取不会刷新它 |
| `partialResult` | object \| null | **仅 RUNNING 期间可能出现**：模板层/确定性层的先行结果，结构同 `result`，见 §8 |
| `result` | object \| null | **仅 SUCCEEDED 时有值**：三层合并后的全量结果，结构同同步接口的 `data`（§4.4） |
| `errorMessage` | string \| null | 仅 FAILED 时有值，用户可读，直接展示 |

任务不存在 / 已过期（30 分钟无更新被清除）/ taskId 属于其他用户 → HTTP 200，`code=30100`，`msg="任务不存在、已过期或无权访问"`。前端应停止轮询并提示重新发起。

### 4.4 `POST /autofill-plan` —— 同步旧接口（**兼容保留，新接入请用异步**）

请求体与 4.2 完全相同。区别：在 Tomcat 工作线程内一直阻塞到模型返回，无 `taskId`、无 `phase`/进度、无 `partialResult`；成功时 `data` 直接是 `PluginAutofillPlanResultVO`。大模型耗时长时易被浏览器/网关超时截断，新接入一律用 §4.2 + §4.3。

`PluginAutofillPlanResultVO`（同步的 `data`、异步的 `result`/`partialResult` 同构）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `fillable` | boolean | 当前页是否适合自动填。`false` 时只展示 `hint`，不执行写入 |
| `siteId` | string \| null | 命中的站点模板 id；未命中模板为 null。**反馈接口需原样透传**。注意：partialResult 中不含 siteId，以最终 `result` 为准 |
| `hint` | string \| null | 不可填或需注意时的中文说明 |
| `assignments` | array | 待写入指令，元素见下表 |
| `blockedFields` | array | 需人工项，元素见下表 |

`assignments[]`（`PluginFieldAssignmentDto`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `selector` | string | 目标控件 CSS 选择器，**保证来自请求快照**（服务端白名单校验，幻觉 selector 已丢弃；并回写为快照中的权威字符串） |
| `value` | string | 首选写入值；复合字段行可能为空（此时必有 `subAssignments`） |
| `order` | number | 建议执行顺序，**最终结果中从 1 连续编号**（模板层在前、确定性次之、LLM 在后） |
| `confidence` | number \| null | 0~1（越界已被钳到 [0,1]）。分层约定值：模板 0.99、clientFieldKey 直映射 0.98、静态别名 0.85、动态别名 0.80 |
| `matchType` | string \| null | 匹配来源，值域见 §6.1（非法值已被置 null，前端仍须 default 兜底） |
| `skipReason` | string \| null | 「仍要写入但需解释不确定性」时用，如 `ambiguous`、`no_matching_option`（已归一为小写）；与 blockedFields 的「不自动填」语义不同 |
| `userHint` | string \| null | 给用户的极短说明，可配黄框/二次确认 |
| `dataFormFieldId` / `i18nLabel` / `clientFieldKey` | string \| null | 请求快照带入的锚点，服务端已合并回显（模型漏抄时从快照补齐） |
| `fallbackValues` | string[] | 下拉备选值，按优先级排序；首选 `value` 匹配不到选项时依次尝试 |
| `subAssignments` | array | 复合字段子赋值：`{selector, value, targetRole, optionLabelsSample}`；`selector` 同样保证来自快照；`targetRole` 值域见 §6.3 |

`blockedFields[]`（`PluginBlockedFieldDto`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `selector` | string | 同 assignments 的白名单保证 |
| `reason` | string | 机器可读原因码，8 值枚举，见 §6.2 |
| `userHint` | string | 用户可读极短中文；模型未给时服务端按 reason 补了默认文案（§6.2） |
| `dataFormFieldId` / `i18nLabel` / `clientFieldKey` | string \| null | 锚点回显，同上 |

### 4.5 `POST /autofill-plan/feedback` —— 逐字段填写反馈

请求体（`PluginAutofillFeedbackRequest`）：

| 字段 | 必填 | 说明 |
|---|---|---|
| `taskId` | 否 | 关联的异步任务 id；同步路径或无任务时可空 |
| `resumeId` | 是 | 为 null 报 30001 |
| `pageUrl` | 否 | 填写发生的页面 URL；服务端会提取 host 做站点聚合 |
| `siteId` | 否 | 从最终 `result.siteId` 原样透传，用于模板命中率统计 |
| `fields` | 否 | 逐字段反馈数组（可为空）；元素：`{selector, clientFieldKey, status, failReason, actualValue}`，`status`/`failReason` 值域见 §6.5，`actualValue` 为实际写入值（服务端入库前会再做一次 PII 脱敏并截断到 64 字符） |

响应：`code=0`，`data=null`。**反馈是异步入队**，返回值不代表已入库；非法 `status` 的字段会被**整行静默丢弃**（见 §6.5），前端不要因为「上报成功」就假定所有行都被接受。

### 4.6 `GET /autofill-feedback/stats` —— 我的填写反馈统计

Query 参数：`days`（可选，统计近 N 天；缺省或 ≤0 按 **30**，上限 **90**）、`siteHost`（可选，按站点精确过滤，服务端转小写匹配）。

响应 `data`（`PluginAutofillFeedbackStatVO`）：

| 字段 | 说明 |
|---|---|
| `days` | 实际生效的统计天数 |
| `totalFields` / `filledCount` / `skippedCount` / `errorCount` | 各状态字段数 |
| `fillRate` | `filledCount / totalFields`；totalFields=0 时为 0 |
| `byFailReason` | `[{reason, count}]`，按 count **降序** |
| `byClientFieldKey` | `[{key, total, filled, fillRate}]`，按 fillRate **升序**（最差的在前），Top 20 |
| `bySiteHost` | 同上结构，按站点聚合，Top 20 |

---

## 5. 页面快照采集规范（`PluginPageFormFieldDto`）

`page.fields[]` 每个元素的字段如下。**采集质量直接决定命中率**：`clientFieldKey` + `labelText` 是确定性匹配的全部输入，`optionLabelsSample` 决定下拉字段能不能不经过 LLM 直接命中。

| 字段 | 必填 | 采集要求 |
|---|---|---|
| `selector` | **是** | 扩展侧可稳定用 `document.querySelector` 定位的 CSS 选择器（建议带唯一 id 或稳定路径）。空白报 30001。服务端会把模型回传的 selector 与快照按「去首尾空白+压缩连续空白」归一化比对，匹配后回写快照原文——所以采集时怎么写，执行时就怎么用 |
| `tagName` | 否 | 标签名小写：`input`/`textarea`/`select` 等。`textarea` 会被确定性层跳过、固定交 LLM 做长文本拼接 |
| `inputType` | 否 | `text`/`email`/`tel`/`number`/`date`/`radio`/`checkbox` 等 |
| `name` / `id` | 否 | DOM 属性原值；标签启发式匹配的兜底文本来源 |
| `labelText` | 否但强烈建议 | 尽量解析关联文案：`label` 元素、`aria-label`、邻近文本。确定性匹配会先做归一化（去 `：:*＊` 后缀、`(必填)`、`请输入/请选择/请填写/选择` 前缀等），所以**采原始文案即可，不要自行裁剪** |
| `placeholder` | 否 | 同上，参与标签启发式 |
| `currentValue` | 否 | 采集时控件已有内容。**语义关键**：`currentValue` 非空的字段，站点模板层与确定性层都**不覆盖**（不产生 assignment），直接交 LLM。详见 §7「已有值字段」一条 |
| `visible` | 否 | 是否在视口内且可交互，供模型参考 |
| `dataFormFieldId` | 否 | **锚点三件套 ①**：网申系统内部字段 id（如美团的 `data-form-field-id`）。站点模板匹配的主要依据之一 |
| `i18nLabel` | 否 | **锚点三件套 ②**：国际化/展示用标签键或文案，多站点对齐用 |
| `clientFieldKey` | 否但强烈建议 | **锚点三件套 ③**：扩展侧标准化字段键（如 `resume.phone` 风格的注册表 key）。命中注册表时走 confidence=0.98 的直映射，是最准最快的路径。三个锚点会在响应 assignments/blockedFields 中原样带回 |
| `compoundSegment` | 条件必填 | 本条 DOM 在「同一视觉行复合 UI」中的语义分段，英文小写：`dial_code`/`local_number`（电话）、`id_type`/`id_number`（证件）、`date_start`/`date_end`（起止时间）。需与模型输出的 `subAssignments[].targetRole` 对齐。**标了 compoundSegment 的字段确定性层一律跳过交 LLM** |
| `interactionMode` | 否 | 自由字符串，建议统一枚举：`plain_text`、`textarea`、`single_select`、`multi_select`、`combobox_typeahead`（先输入再点选）、`composite_left_select`、`composite_right_text`、`date_picker`。服务端只对 `textarea`（不区分大小写）有特殊逻辑，其余供模型参考与前端自查 |
| `optionLabelsSample` | 否 | 下拉/联想**已展开时可见**的前若干项文案，不必全量，**上限 40 条**（超出报 30001「optionLabelsSample 条数过多」）。提供后：确定性层可做选项匹配直接命中；模型被约束「value 必须原样取自其中某项」。采不到就留空，不要编造 |

**复合字段「同一视觉行拆多个独立 selector 行」约定**：手机号「区号下拉 + 号码输入」这类同一视觉行的复合控件，采集时拆成**多行独立 `fields[]` 元素**（每个子控件一行、各自有独立 `selector`），并分别标 `compoundSegment`（左 `dial_code`、右 `local_number` 等）与合适的 `interactionMode`（左 `composite_left_select`、右 `composite_right_text`）。服务端会把它合并成**一条**带 `subAssignments` 的 assignment 返回，前端执行时按子 selector 分别写入（§7）。

**数量上限**：单页 `fields` 超过 **120** 条时服务端只取前 120 条（后续 selector 白名单也只认这批）。超长页面建议先过滤掉明显无关控件（隐藏域、搜索框等）再提交。

---

## 6. 枚举值域总表（前端 switch 必须有 default 兜底）

### 6.1 `assignments[].matchType`

| 值 | 来源层 | confidence 约定 | 说明 |
|---|---|---|---|
| `template` | 站点模板 | 0.99 | 命中 ATS 站点模板的金标直填 |
| `deterministic` | 确定性匹配 | 0.98（clientFieldKey 直映射）/ 0.85（静态别名表） | 不经 LLM |
| `dynamic_alias` | 确定性匹配 | 0.80 | 命中 DB 动态别名表（admin 审核登记） |
| `exact` / `fuzzy` / `guess` | LLM | 模型自评 | 提示词约定的三态 |
| `null` | — | — | LLM 输出非法 matchType 时被服务端置 null |

⚠️ **重要**：`template` 与 `dynamic_alias` 由模板层/确定性层直接产出，**不经过 LLM 清洗层的 matchType 白名单**（白名单 `exact/fuzzy/guess/deterministic` 只作用于 LLM 结果）；且白名单允许 LLM 也输出 `deterministic`。因此前端对 `matchType` 的 switch **必须有 default 分支**，未知值按低置信展示即可，不要抛异常。

### 6.2 `blockedFields[].reason`（`BlockedFieldReasons`，8 值）

服务端已归一化（小写、非法值归 `other`），并在模型没给 `userHint` 时补默认文案：

| reason | 含义 | 默认 userHint |
|---|---|---|
| `readonly` | 只读控件 | 只读项，请手动确认 |
| `captcha` | 验证码 | 验证码需人工输入 |
| `attachment` | 附件上传 | 附件需本地上传 |
| `no_mapping` | 无法与简历字段建立映射 | 无法与简历字段对应 |
| `ambiguous` | 语义不明确 | 语义不明确，请手动选择 |
| `compound_unresolved` | 复合字段未能拆分 | 复合字段未能拆分，请分步填写 |
| `policy` | 策略限制（如富文本编辑器不允许自动填） | 策略限制，请手动填写 |
| `other` | 其他/兜底 | 需人工处理 |

### 6.3 `subAssignments[].targetRole`（`normalizeTargetRole`）

合法值（小写）：`dial_code`、`local_number`、`id_type`、`id_number`、`date_start`、`date_end`、`part`（泛化多段）。**非法值被服务端置 `null`**——前端遇到 null 或未知值时忽略该角色、直接按子 `selector` 写入即可。

### 6.4 任务 `status` 与 `phase`

`status`：`PENDING`（已创建未执行，percent=0，message=「任务已创建，等待调度」）→ `RUNNING`（工作线程接管，percent≥5）→ 终态 `SUCCEEDED`（带 `result`）/ `FAILED`（带 `errorMessage`，percent=100）。

`phase` 枚举（`PluginAutofillProgressPhase`）与代码中实际推送的 percent：

| phase | percent（代码实际值） | 含义 |
|---|---|---|
| `RECEIVED` | 0（PENDING 初始）→ 5（RUNNING 接管）→ 8（请求体校验完） | 请求入队/校验 |
| `LOADING_RESUME` | 12 | 加载简历 draft（优先 Redis 预加载缓存） |
| `DETERMINISTIC_MATCHING` | 15（模板匹配中）→ 18（模板完成）→ 22（确定性匹配中）→ 26（确定性完成） | 站点模板匹配 + 确定性匹配（同一 phase 名） |
| `BUILDING_PROMPT` | 28 | 组装提示词（仅未匹配字段） |
| `CALLING_MODEL` | 38 | 等待大模型返回，**可能在此停留数十秒～数分钟，属正常** |
| `PARSING_MODEL_JSON` | 65 | 模型结果解析 |
| `SANITIZING_PLAN` | 78（常规）；90（fields 为空短路径） | selector 白名单、锚点合并、blocked 清洗 |
| `COMPLETED` | 100 | 成功结束（含缓存命中、全字段免 LLM 等短路径） |
| `FAILED` | 100 | 失败，`errorMessage` 可读 |

percent 不保证严格单调之外的含义，仅供进度条；`DETERMINISTIC_MATCHING` 一个 phase 覆盖模板+确定性两阶段是代码现状，前端文案可合并展示为「快速匹配中」。

### 6.5 反馈 `status` 与 `failReason`

`fields[].status`（白名单）：`filled` / `skipped` / `error`。
`fields[].failReason`（白名单）：`option_not_found` / `readonly` / `date_nav_failed` / `selector_miss` / `other`。

> ⚠️ **M2 起的服务端行为**（`PluginAutofillFeedbackService.sanitizeFields`）：
> - `status` 非法（含大小写不符、null）→ **该字段整行丢弃**，不进统计；
> - `failReason` 非法 → **归一为 `other`**（空值保持空，不归一）。
> 服务端不做大小写归一（仅 trim），请前端严格小写上送。

### 6.6 `interactionMode` / `compoundSegment` 值域

均为**自由字符串**，服务端不校验白名单，但见 §5 的建议枚举。服务端特殊逻辑只有两处：`interactionMode` 归一小写后等于 `textarea`（或 tagName 为 textarea，或 clientFieldKey 命中长文本后缀表）→ 确定性层跳过交 LLM；`compoundSegment` 非空 → 确定性层跳过交 LLM。

---

## 7. 填入执行规范

1. **执行顺序**：按 `assignments[].order` 升序执行（最终结果中 order 从 1 连续编号），配合「逐格高亮」动画。
2. **定位优先级**：优先用锚点（`dataFormFieldId` → `clientFieldKey` → `i18nLabel`）定位 DOM，`selector` 兜底。服务端已保证 selector 来自快照原文，`document.querySelector` 直接可用。
3. **复合字段写入规则**（`subAssignments` 非空时）：
   - **禁止**用主 `value` 覆盖复合行的任一子控件（主 value 此时通常为空或仅为展示用）；
   - 逐条按 `subAssignments[].selector` 定位子节点写入其 `value`；`targetRole` 用于识别语义（如 `dial_code` 行写下拉选项文案 `+86`，`local_number` 行写号码主体），为 null 时直接按 selector 写；
   - 区号/证件类型这类**左侧下拉**的 value 是与页面选项一致的短文案，按下拉逻辑选；**号码主体**按文本框写入。
4. **下拉与 `fallbackValues`**：首选 `value` 在可见选项中匹配不到时，按 `fallbackValues` 顺序逐个尝试；全部失败记反馈 `status=error, failReason=option_not_found`。
5. **typeahead（`combobox_typeahead`）键盘模拟**：纯 `input.value=` 通常无效，需模拟 `focus → 逐字输入 → dispatchEvent(input) → 等待联想列表 → 点击匹配项`。
6. **已有值字段**：`currentValue` 非空的字段，模板层与确定性层**不会**产出 assignment（服务端不覆盖站点预填/用户已填）。⚠️ 但这些字段会作为「未匹配」进入 LLM 的输入（当前提示词未约束模型跳过它们），**最终 result 里仍可能出现指向已有值控件的 assignment**——前端执行时建议默认跳过当前 DOM 中已有非空值的控件（或按产品策略二次确认），不要盲目覆盖用户输入。
7. **`blockedFields`**：红框标注 + 展示 `userHint`（可按 `reason` 做 i18n，§6.2），交人工处理；不要自动写。
8. **`fillable=false`**：不执行任何写入，只展示 `hint`（`blockedFields` 仍可标红）。
9. **`skipReason` / `userHint`（assignment 级）**：值仍要写入，但可配黄框/二次确认提示用户；不要把它当成「不填」。
10. **写值后触发事件**：赋值后 dispatch `input`/`change` 事件，保证 React/Vue 受控组件感知。

---

## 8. `partialResult` 增量填入（RUNNING 期间）

**推送时机与内容**（代码 `PluginFormAutofillAiService.planAutofill`，仅异步路径；同步接口 noop 不推送）：

- **第一次**：站点模板匹配完成后、且模板命中数 > 0 时推送。`assignments` = 仅模板层结果，`hint` = 「站点模板已命中并完成部分字段直填，剩余字段继续处理」。percent≈18。
- **第二次**：确定性匹配完成后、且确定性命中数 > 0 时推送。`assignments` = 模板 + 确定性**累计**结果，`hint` = 「确定性匹配已完成，剩余字段正在调用 AI 处理」。percent≈26。**第二次整体覆盖第一次**（任务快照里只有一份 partialResult）。
- 两次推送的 `fillable` 均为 `true`，且**都不带 `siteId`**。
- 若某层命中数为 0 则不推送该层；若模板+确定性已覆盖全部字段，则**跳过 LLM 直接成功**；若命中结果缓存，则**直接 SUCCEEDED**，全程无 partialResult。

**前端处理准则**（依据 `mergeResults` 的合并语义）：

1. `RUNNING` 且 `partialResult != null` 时，可**立即对其 `assignments` 执行填入**，不必等终态——这是本特性存在的意义（模板/确定性结果毫秒级可得，LLM 可能要等几十秒）。
2. 每收到一次 partialResult，按「已填 selector 集合」做**增量去重**：partial 内容是累计快照而非增量包，重复 selector 不要重复写。
3. **最终以 `result` 为准**：`SUCCEEDED` 后忽略 `partialResult`，用 `result` 补齐剩余字段。合并优先级为 **模板 > 确定性 > LLM**（按归一化 selector 去重，低层重复的丢弃），且 LLM 只处理前两层未匹配的字段——因此 **partial 期间已填字段的值在最终 `result` 中原样保留，不会被 LLM 推翻**；`result` 是 partial 的超集，前端只需补填「result 有而 partial 没有」的 selector。
4. 注意 `result.assignments` 的 `order` 是**合并后重新连续编号**的，与 partial 中的 order 可能不同；增量场景建议以 selector 为键做幂等，不要依赖 order 做「已填」判断。
5. `blockedFields` 同理：partial 中可能已含模板/确定性识别的 blocked，`result` 中是三层合并去重后的全量，标红以 `result` 为准做并集即可。

---

## 9. 轮询建议

- **间隔**：首次轮询可在提交后 300~800ms 开始，之后 **500ms~2s** 一次；不要低于 200ms。CALLING_MODEL 阶段（percent≈38）可能数十秒~数分钟无变化，属正常，不要当卡死。
- **本地超时**：建议设一个本地上限（如 3~5 分钟），超时停止轮询、提示用户重试；服务端不会因为客户端停轮而中断后台任务。
- **终态清理**：`SUCCEEDED` / `FAILED` / 轮询接口 `code !== 0` 三种情况都必须清除定时器、恢复按钮状态。
- **防连点**：点击「一键填入」后立即禁用按钮直至终态；同一页快速连点会产生多个 taskId 并重复扣 AI 额度。
- **taskId 归属与过期**（`PluginAutofillTaskRegistry`）：
  - 任务存于**服务端进程内存**（非 Redis）。taskId 与提交的 `userId` 绑定，换账号轮询会得到「任务不存在、已过期或无权访问」（30100）。
  - **TTL = 30 分钟**，以「最后一次服务端进度更新」起算；**轮询读取不会续期**。任务到达终态后不再更新，约 30 分钟后被清除——拿到终态后请消费结果并丢弃 taskId，不要长期持有。
  - 极端情况：单次模型调用超过 30 分钟且中间无任何进度推送时，任务可能被提前清除（此时后台任务仍会跑完，但轮询已查不到）。
  - 进程重启 / 多副本部署时任务不可见（未上共享存储），轮询报 30100 时提示用户重新提交即可。
- **提交也可能失败**：线程池队列满（容量 200）等极端并发下，提交接口本身可能返回 `code=10001`，前端按普通错误提示重试。

---

## 10. 反馈上报规范

- **时机**：每个字段执行完在内存里记录一条 `{selector, clientFieldKey, status, failReason?, actualValue?}`；整页执行结束（或用户关闭/提交页面）时**批量一次** `POST /autofill-plan/feedback`。不必逐字段发请求。
- **`taskId` 可选**：异步流程带上本次 `taskId` 便于关联；同步流程或拿不到时可空。
- **`siteId` 透传**：从最终 `result.siteId` 原样带回（partialResult 里没有 siteId，务必等 SUCCEEDED 后取）。未命中模板时为 null，可空上送。
- **值域**：严格按 §6.5 白名单小写上送——非法 `status` 整行被丢弃、非法 `failReason` 被归一为 `other`，且接口仍返回成功，前端无从察觉，务必自检。
- **`actualValue` 脱敏**：服务端入库前会再做一次 PII 掩码（身份证/手机号/邮箱）并截断到 64 字符；但**前端不应上报无关 PII**——`actualValue` 只用于核对「实际写入了什么」，不要附带与字段无关的页面数据。
- **响应语义**：`code=0` 仅代表「已异步入队」，不代表已入库、也不代表所有字段行都被接受（见上条）。反馈链路失败不影响主流程，前端无需重试。

---

## 11. 前端自查清单（Checklist）

**鉴权与响应壳**
- [ ] 所有请求带 `Authorization: Bearer <token>`；HTTP 401 / `code=30007` 时引导重新登录。
- [ ] 统一从 `ResponseDTO.data` 取业务对象；`code !== 0` 时展示 `msg`；`success`/`message` 与 `ok`/`msg` 只选一组使用。
- [ ] 识别 `code=30200`（AI 额度不足）并引导充值/兑换。

**autofill-context 预展开**
- [ ] 采快照**之前**调 `GET /{resumeId}/autofill-context`，按 `moduleCounts` 展开「+添加」块（允许多、不宜少）。
- [ ] 用 `schemaVersion`/`updatedAt` 做本地缓存失效判断，不凭本地猜测块数。
- [ ] 按 `dateFieldPrecision` 区分完整日期 / 仅年月控件的操作方式。
- [ ] 页面无法自动展开时降级为提示用户手动展开后再采快照。

**快照采集**
- [ ] `selector` 可被 `document.querySelector` 稳定定位；`fields` 不为 null；单页 ≤120 条。
- [ ] `labelText`/`placeholder` 采原始文案（服务端做归一化，不要自行裁剪）。
- [ ] 锚点三件套（`dataFormFieldId`/`i18nLabel`/`clientFieldKey`）能采尽采。
- [ ] 复合字段按「同一视觉行拆多个独立 selector 行」+ 标 `compoundSegment`/`interactionMode`。
- [ ] `optionLabelsSample` 只采可见项、≤40 条、不编造；`currentValue` 如实采集。

**异步任务与轮询**
- [ ] 新接入走 `POST /tasks` + 轮询，不用同步接口；提交前禁用按钮防连点。
- [ ] 轮询间隔 500ms~2s；终态/报错后清定时器；本地有超时兜底。
- [ ] 「任务不存在、已过期或无权访问」时停止轮询并提示重新发起。
- [ ] 不把 percent 长时间停在 ~38%（CALLING_MODEL）判定为卡死。

**partialResult 增量填入**
- [ ] `RUNNING + partialResult != null` 时立即开始增量填入。
- [ ] partial 按 selector 幂等去重（partial 是累计快照，会重复下发已匹配项）。
- [ ] `SUCCEEDED` 后以 `result` 为准补填差集；已知 partial 已填值不会被推翻。
- [ ] 不依赖 partial 的 `order` 做已填判断（result 会重排 order）。

**填入执行**
- [ ] 按 `order` 执行；锚点优先、selector 兜底；赋值后 dispatch input/change 事件。
- [ ] 复合行只按 `subAssignments[].selector` 写入，**禁止**用主 `value` 覆盖复合行。
- [ ] 下拉首选值失配时依次尝试 `fallbackValues`；`combobox_typeahead` 用键盘模拟而非直接赋 value。
- [ ] 默认跳过 DOM 中已有非空值的控件（LLM 层仍可能对其下发 assignment）。
- [ ] `blockedFields` 标红展示 `userHint`；`fillable=false` 只展示 `hint` 不写入。

**枚举兜底**
- [ ] `matchType` switch 有 default（可能收到 `template`/`deterministic`/`dynamic_alias`/`exact`/`fuzzy`/`guess`/null/未来新值）。
- [ ] `reason` switch 覆盖 8 值 + default；`reason=attachment` 有「附件需本地上传」类人工引导。
- [ ] `targetRole` 为 null 或未知时忽略角色、按子 selector 写入。
- [ ] `status`/`phase` 展示层对未知值有兜底文案。

**反馈上报**
- [ ] 逐字段记录、整页批量上报一次；带 `taskId`（如有）与 `result.siteId` 透传。
- [ ] `status` 严格 `filled/skipped/error`、`failReason` 严格五值之一（知道：非法 status 整行丢弃、非法 failReason 归 `other`）。
- [ ] `actualValue` 不含无关 PII。

---

## 附录：与旧文档的不一致清单（前端排查用）

以下是写作本文时对照代码发现的「旧文档说过但已过时/不准确」的点：

1. **`plugin-autofill-async-frontend-integration.md` §6 易错点第 2 条**「assignments 只在 SUCCEEDED 的 result 里；轮询过程中没有填表列表」——**已过时**。当前 RUNNING 期间可能有 `partialResult`（模板层/确定性层先行结果），该旧文完全没有 `partialResult` 字段的描述。
2. **同一篇 §3.1 的 `phase` 示例列表缺 `DETERMINISTIC_MATCHING`**——该阶段（模板匹配+确定性匹配共用此 phase 名，percent 15→26）是当前耗时感知的第一段，前端进度文案若按旧表映射会漏。
3. **同一篇 §4.3「首次轮询 300~800ms」**与 Controller javadoc「建议 500ms~2s」并存——两者都不算错，本文统一为：首次 300~800ms、之后 500ms~2s。
4. **旧文档均未说明任务 TTL 的精确语义**：实际是「30 分钟无**服务端进度更新**即清除，轮询读取不续期」，而非「提交后 30 分钟」或「轮询保活」。
5. **`PluginFieldAssignmentDto` 类注释及旧文档暗示 matchType 只有 `exact/fuzzy/guess`**——实际响应还有 `template`（0.99）、`deterministic`（0.98/0.85）、`dynamic_alias`（0.80），且后两者不经过 LLM 清洗层白名单；前端必须 default 兜底。
6. **`plugin-autofill-quality-frontend-adaptation.md` 的 API 摘要**只列了 autofill-context 和 tasks 两个接口，缺反馈接口（`POST .../feedback`、`GET /autofill-feedback/stats`）——反馈是 M2 闭环的一部分，新接入必须实现。
7. **「已有值字段服务端不会下发 assignment」这一说法只对模板层/确定性层成立**：`currentValue` 非空的字段会被这两层跳过，但仍作为未匹配字段进入 LLM 输入，最终 result 中**可能**出现针对它们的 assignment。前端应以「DOM 中已有非空值则跳过」为默认执行策略，不能假定服务端已过滤。
8. **`partialResult` 不含 `siteId`**：反馈需要的 `siteId` 只能从最终 `result` 取；若前端在 partial 阶段就发反馈，siteId 会是 null。
9. **结果缓存的存在旧文档未提**：同用户+同简历版本+同页面结构 2 小时内重复提交会**秒回且无 partialResult**；页面指纹含 selector/labelText/placeholder/optionLabelsSample 且**对字段顺序敏感**——扩展采集顺序不稳定会击穿缓存（表现为时快时慢，非 bug）。
10. **反馈接口「成功」语义**：`code=0` 仅表示异步入队；非法 `status` 的行被静默丢弃（M2 起），前端不能从响应判断反馈是否全部被接受。

---

*本文档所有行为描述对应代码版本：M2 之后（含三层降级链、partialResult、反馈白名单、结果缓存）。后续后端改动请同步修订本文。*
