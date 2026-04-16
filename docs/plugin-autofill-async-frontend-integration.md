# 一键填表 · 异步接口 · 前端对接说明

面向：浏览器扩展 / 插件前端。下文只描述**异步路径**及与同步路径的差异。

---

## 1. 接口清单

| 用途 | 方法 | 路径 |
|------|------|------|
| 提交异步任务 | `POST` | `{BASE}/api/v1/plugin/resume/autofill-plan/tasks` |
| 轮询任务状态 | `GET` | `{BASE}/api/v1/plugin/resume/autofill-plan/tasks/{taskId}` |

`{BASE}` 为插件服务端根地址（与现有插件 API 一致）。`{taskId}` 为提交接口返回的 UUID 字符串。

**请求体**：`POST .../tasks` 与原有同步接口 **`POST .../autofill-plan` 完全相同**（`resumeId` + `page` 快照等），无需为异步单独改 payload 结构。

**鉴权 / Header**：与当前其它插件接口保持一致（如 `Authorization: Bearer ...`、以及你们已约定的用户标识头）；未登录或 token 无效时仍按统一 `ResponseDTO` 错误返回。

**统一响应壳**（所有成功/业务失败均如此解析）：

- `code === 0` 且 `ok === true`：业务成功，业务数据在 **`data`**。
- `code !== 0`：失败，读 **`msg`**；通常 **`data` 为 null**（提交失败、任务不存在等）。

---

## 2. 提交任务：`POST .../autofill-plan/tasks`

### 2.1 成功时 `data` 类型

`data` 为对象，字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | `string` | 轮询用的唯一 id，必存 |
| `pollPathTemplate` | `string` | 提示用路径模板；**实现时建议直接拼**：`` `${BASE}/api/v1/plugin/resume/autofill-plan/tasks/${taskId}` ``，不要依赖模板里的占位符解析方式 |

### 2.2 前端应立即做的事

1. 持久化 `taskId`（内存即可；若需断线续跑可再考虑 `chrome.storage` 等）。  
2. 进入**轮询状态**（见第 4 节），并展示「已提交、生成中」类 UI。  
3. **不要**在本请求的 `data` 里找 `assignments`——提交接口**不返回**填表结果。

---

## 3. 轮询：`GET .../autofill-plan/tasks/{taskId}`

### 3.1 成功时 `data` 字段（轮询快照）

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | `string` | 与提交一致 |
| `status` | `string` | **`PENDING`** \| **`RUNNING`** \| **`SUCCEEDED`** \| **`FAILED`**（大小写与后端一致，请用严格比较或白名单映射） |
| `phase` | `string` | 细阶段，与后端枚举 `name()` 一致，例如：`RECEIVED`、`LOADING_RESUME`、`BUILDING_PROMPT`、`CALLING_MODEL`、`PARSING_MODEL_JSON`、`SANITIZING_PLAN`、`COMPLETED`、`FAILED`。**可做步骤条/副标题**，不必对用户逐字翻译，可映射为简短中文 |
| `message` | `string` | 给人看的短说明，可与 `phase` 一起展示 |
| `percent` | `number` | **0～100**，用于进度条。**在 `CALLING_MODEL` 附近可能长时间不变**，属正常，不要当卡死 |
| `createdAtEpochMs` | `number` | 毫秒时间戳，可算「已等待多久」 |
| `updatedAtEpochMs` | `number` | 最近一次状态更新时间 |
| `result` | `object \| null` | **仅当 `status === 'SUCCEEDED'` 时有值**；形状与同步 `POST .../autofill-plan` 的 **`data` 完全一致**（见下节） |
| `errorMessage` | `string \| null` | **仅当 `status === 'FAILED'` 时有值**；展示给用户 |

### 3.2 `result` 内可拿到的业务字段（与同步接口相同）

当且仅当 `status === 'SUCCEEDED'` 且 `result != null` 时：

| 字段 | 说明 |
|------|------|
| `result.fillable` | `boolean`：是否适合自动填；为 `false` 时一般只展示 `hint`，不执行自动写入 |
| `result.hint` | 提示文案（含不可填原因等） |
| `result.assignments` | 数组：每条含 `selector`、`value`，以及可选的 `order`、`confidence`、`matchType`、`skipReason`、`userHint`、`dataFormFieldId`、`i18nLabel`、`clientFieldKey`、`subAssignments` 等 |
| `result.blockedFields` | 数组：需人工项，含 `selector`、`reason`、`userHint` 及锚点字段等 |

**执行 DOM 填写的数据源**：与原先一样，以 **`result.assignments`**（及你们已实现的 `subAssignments`、锚点回写策略）为准；**不要**从轮询根的 `message` 里解析 JSON。

### 3.3 轮询失败（HTTP 200 但 `code !== 0`）

常见：`msg` 含「任务不存在、已过期或无权访问」。处理：停止轮询，提示用户重新发起一键填表。

---

## 4. 推荐前端流程与状态机

### 4.1 状态（本地 UI）

建议至少区分：

- `idle`：未开始  
- `submitted`：已拿到 `taskId`，正在轮询  
- `succeeded`：`status === 'SUCCEEDED'`，已消费 `result`  
- `failed`：`status === 'FAILED'` 或提交/轮询接口 `code !== 0`  
- `timeout`（可选）：本地等待超过上限，停止轮询并提示重试

### 4.2 推荐步骤

1. `POST .../tasks` → 若 `code !== 0`，进 `failed`，展示 `msg`。  
2. 成功则保存 `taskId`，置 `submitted`，启动定时器。  
3. 定时器内 `GET .../tasks/{taskId}`：  
   - `code !== 0` → `failed`，展示 `msg`，**停止轮询**。  
   - `status` 为 `PENDING` / `RUNNING` → 更新 UI：`percent`（进度条）、`message` 或 `phase` 映射文案；**继续轮询**。  
   - `status === 'SUCCEEDED'` → 取 **`data.result`**，按与同步版相同逻辑执行填表 / 高亮 / `blockedFields` 标红；**停止轮询**。  
   - `status === 'FAILED'` → 展示 **`data.errorMessage`**；**停止轮询**。

### 4.3 轮询间隔与退避

- 建议首次轮询可在 **300～800ms** 后开始，之后 **500ms～2s** 一次；**不要**低于 200ms 高频刷。  
- 可在前几次用较短间隔，超过 30s 后略加大间隔，降低服务端压力。  
- 必须在 **`SUCCEEDED` / `FAILED` / 接口错误`** 三种情况下清除定时器，避免泄漏与无意义请求。

### 4.4 可做的 UI（字段如何拼）

| UI 元素 | 建议数据来源 |
|---------|----------------|
| 主进度条 | `percent`（注意平台端非线性，仅示意） |
| 当前步骤 / 副标题 | `message`，或 `phase` → 你们维护的中文映射表 |
| 已等待时间 | `Date.now() - createdAtEpochMs`（本地计算） |
| 成功后的填表 | **`result.assignments`** 等，与同步接口一致 |
| 需人工标红 | **`result.blockedFields`** |
| 不可整页自动填 | **`result.fillable === false`** 时以 `hint` 为主，避免强行写 DOM |

---

## 5. 与同步接口的取舍

| 场景 | 建议 |
|------|------|
| 网关/扩展超时多、需要进度条 | 用 **异步** `.../tasks` + 轮询 |
| 极简实现、内网且超时足够大 | 可继续 **同步** `.../autofill-plan`，直接读 `data` 为 `PluginAutofillPlanResultVO` |

两套接口返回的**最终填表数据结构**一致：异步成功时在 **`GET` 的 `data.result`**，同步在 **`POST` 的 `data`**。

---

## 6. 必须注意的问题（易错点）

1. **始终从 `ResponseDTO.data` 取业务对象**，不要假定业务字段在根上。  
2. **`assignments` 只在 `SUCCEEDED` 的 `result` 里**；轮询过程中没有填表列表。  
3. **`percent` 可能长时间不变**：尤其大模型阶段，不要据此判定后端挂了。可结合「超时 + 重试」策略。  
4. **服务端任务存在内存**：插件进程重启或任务过期后，轮询会得到业务错误；应提示用户**重新提交**。  
5. **多副本部署**（多台插件服务）：若未上共享存储，轮询可能命中无此任务的实例；属基础设施问题，前端可做「失败则提示重试」。  
6. **用户切换账号**：须用新 token 请求；旧 `taskId` 可能因 `userId` 校验返回无权访问。  
7. **并发**：同一页快速连点「一键填表」可能产生多个 `taskId`；建议提交前禁用按钮，或取消上一轮询再开新任务。

---

## 7. 自检清单（上线前）

- [ ] 提交成功后是否**一定**启动轮询，且**只**用返回的 `taskId` 拼 URL？  
- [ ] 是否在终态与错误路径**清除**轮询定时器？  
- [ ] 是否在 `SUCCEEDED` 时用 **`data.result`** 走与同步版相同的写 DOM 逻辑？  
- [ ] 是否在 `FAILED` 与 `code !== 0` 时向用户展示明确文案？  
- [ ] 轮询间隔是否合理（避免压垮服务端）？
