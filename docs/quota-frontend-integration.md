# AI 额度模块前端对接文档

## 1. 模块概述

AI 额度模块是平台所有 AI 功能的"计费闸门"，包含：

- 查看当前额度总览（余额、累计额度、会员等级）
- 查看额度流水（每日免费发放、AI 消耗、兑换码充值等）
- 兑换码充值（测试期过渡方案，正式支付通道后续接入）

> **重要：** 额度不足拦截发生在**所有 AI 功能接口**（简历解析、一键填表、面试复盘总结、模拟面试生成），
> 这些接口返回的错误码 `30200` 需要前端**全局统一处理**，详见第 5 节。
>
> 所有接口均需要 **Bearer Token** 鉴权。

---

## 2. 基础信息

| 项 | 值 |
|---|---|
| 基础路径 | `http://localhost:8088/api/v1/app` |
| 接口模块 | `/quota` |
| 鉴权方式 | `Authorization: Bearer <token>` |
| Content-Type | `application/json` |
| 响应包装 | 统一返回 `ResponseDTO<T>`，业务数据在 `data` 字段 |

### 2.1 通用响应结构

```json
{
  "code": 0,
  "ok": true,
  "success": true,
  "msg": "操作成功",
  "message": "操作成功",
  "data": { ... }
}
```

- `code == 0` 且 `ok == true`（`success` 为 `ok` 的兼容别名）表示业务成功。
- 业务异常返回 `code != 0`，`msg` / `message` 中携带中文提示。

### 2.2 本模块相关错误码

| code | 含义 | 触发场景 |
|---|---|---|
| `30200` | AI 额度不足，请充值或明日再来 | **任意 AI 功能接口**在校验额度时余额不足 |
| `30201` | 兑换码无效或已过期 | 兑换码不存在 / 已使用 / 已过期 / 已禁用 |
| `30100` | 操作无法完成 | 其他业务失败（如生成兑换码参数非法） |

---

## 3. 额度规则说明（前端展示文案依据）

| 规则 | 当前默认值 | 说明 |
|---|---|---|
| 每日免费额度 | 10 点 | 每天首次调用任意 AI 功能时自动发放（懒发放），不需要前端调用任何接口 |
| 计费换算 | 1000 token = 1 点 | 按真实消耗 token 向上取整扣点 |
| 每次最低扣点 | 1 点 | 单次 AI 调用至少扣 1 点 |
| 欠费机制 | 余额可为负 | 单次调用按真实 token 结算，可能把余额扣成负数（欠费）；欠费后所有 AI 功能被拦截（30200），后续每日免费额度、兑换码、充值会**优先抵扣欠款**，补足后才能继续使用 |
| 消耗场景 | 简历智能解析、插件一键填表（LLM 兜底阶段）、面试复盘语音总结、模拟面试生成 | 命中缓存/模板直填的填表请求不消耗额度 |

> 流水类型（`transactions` 接口返回的 `type` 字段）与建议展示文案：
>
> | type | 建议展示 |
> |---|---|
> | `daily_grant` | 每日免费额度 |
> | `consume` | AI 功能消耗 |
> | `redeem` | 兑换码充值 |
> | `recharge` | 在线充值（预留，M4 支付上线后使用） |
> | `admin_adjust` | 系统调整 |

---

## 4. 接口清单

### 4.1 查询额度总览

```http
GET /api/v1/app/quota/balance
Authorization: Bearer <token>
```

**响应 `data`**：

```json
{
  "balance": 35,
  "totalQuota": 120,
  "memberLevel": "FREE",
  "lastDailyGrantDate": "2026-07-21"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `balance` | number | 当前可用额度（点），**页面主展示数字**；**可为负数（欠费）**，欠费时建议显示"欠费 X 点"并弱化/禁用各 AI 功能入口 |
| `totalQuota` | number | 累计发放过的总额度（含每日免费+充值+兑换） |
| `memberLevel` | string | 会员等级，当前固定 `FREE`（预留），前端可先隐藏或展示"免费版" |
| `lastDailyGrantDate` | string | 最近一次每日免费额度发放日期（YYYY-MM-DD），可用于提示"今日免费额度已到账" |

---

### 4.2 查询额度流水

```http
GET /api/v1/app/quota/transactions?limit=20
Authorization: Bearer <token>
```

**Query 参数**：

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `limit` | 否 | 20 | 最大返回条数，上限 100，超出按 100 处理 |

**响应 `data`**：

```json
[
  {
    "id": 9001,
    "type": "consume",
    "points": -3,
    "balanceAfter": 35,
    "remark": "AI 调用消耗：mock_interview_generate",
    "createdAt": "2026-07-21T10:30:00"
  },
  {
    "id": 9000,
    "type": "daily_grant",
    "points": 10,
    "balanceAfter": 38,
    "remark": "每日免费额度",
    "createdAt": "2026-07-21T09:00:00"
  }
]
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `points` | number | 变动点数：**增加为正、消耗为负**，前端可直接用正负号染色 |
| `balanceAfter` | number | 该笔变动后的余额快照 |
| `remark` | string | 备注。`consume` 类型会带业务类型后缀，可选映射： `resume_parse`=简历解析、`plugin_autofill`=一键填表、`interview_summarize`=面试总结、`mock_interview_generate`=模拟面试 |

---

### 4.3 兑换码充值

```http
POST /api/v1/app/quota/redeem
Authorization: Bearer <token>
Content-Type: application/json

{
  "code": "A1B2C3D4E5F6A7B8C9D0E1F2"
}
```

**请求字段**：

| 字段 | 必填 | 校验 | 说明 |
|---|---|---|---|
| `code` | 是 | 4-64 位 | 兑换码，后端会自动 trim 并转大写匹配 |

**成功响应 `data`**：

```json
{
  "code": "A1B2C3D4E5F6A7B8C9D0E1F2",
  "points": 100,
  "balanceAfter": 135
}
```

**失败响应示例**：

```json
{
  "code": 30201,
  "ok": false,
  "msg": "兑换码已过期"
}
```

`msg` 可能的值：`兑换码不能为空` / `兑换码不存在` / `兑换码已使用或已禁用` / `兑换码已过期` / `兑换码已被使用`，直接展示给用户即可。

---

## 5. 额度不足的全局处理（重要）

**所有 AI 功能接口**（不仅仅是本模块）在额度不足时都会返回：

```json
{
  "code": 30200,
  "ok": false,
  "msg": "AI 额度不足，请充值或明日再来"
}
```

涉及的接口包括（非穷举）：

| 接口 | 功能 |
|---|---|
| `POST /api/v1/app/resume/upload-parse` | 简历智能解析 |
| `POST /api/v1/app/interview/sessions/{id}/voice-summarize` | 面试复盘语音总结 |
| `POST /api/v1/app/mock-interview` | 模拟面试生成 |
| `POST /api/v1/plugin/resume/autofill-plan` 及异步任务接口 | 插件一键填表（LLM 兜底阶段） |

### 建议的前端实现

1. 在 axios/fetch 响应拦截器中**统一拦截 `code === 30200`**：
   - 弹出"额度不足"引导弹窗（见 7.2），而不是把错误交给各页面各自处理。
   - 弹窗按钮：`去充值`（跳额度中心页）、`明日再来`（关闭）。
2. 其余非 0 code 保持现有各页面自行 toast 的逻辑。

---

## 6. 页面建议

### 6.1 额度中心页

```
┌─────────────────────────────┐
│  我的额度                    │
│  ┌───────────────────────┐  │
│  │   35 点               │  │  ← GET /quota/balance
│  │   今日免费额度已到账    │  │  ← lastDailyGrantDate == 今天 时显示
│  └───────────────────────┘  │
│  [ 兑换码充值 ]              │  ← 展开输入框
│                             │
│  额度流水                    │
│  +10  每日免费额度   09:00   │  ← GET /quota/transactions
│  -3   模拟面试       10:30   │
│  +100 兑换码充值     昨天     │
└─────────────────────────────┘
```

1. 进入页面先调 `GET /quota/balance` 渲染余额卡片。
2. `lastDailyGrantDate` 等于今天日期时，显示"今日免费额度已到账"，否则显示"今日免费额度将在首次使用 AI 功能时到账"。
3. 兑换码入口：点击展开输入框 → 调 `POST /quota/redeem` → 成功后 toast "成功充值 100 点"，并**重新拉取 balance 和 transactions**。
4. 流水列表按 `type` 映射中文标签，正数绿色、负数灰色。

### 6.2 额度不足/欠费引导弹窗（全局组件）

```
┌──────────────────────────┐
│   额度不足                │
│   当前欠费 5 点，           │  ← balance < 0 时显示欠费金额
│   充值/兑换后将优先抵扣欠款。 │
│   明天也会自动到账 10 点。   │
│                          │
│   [去充值]    [明日再来]   │
└──────────────────────────┘
```

> 弹窗出现时建议同时拉取一次 `GET /quota/balance`，把 `balance` 展示进文案（欠费或余额不足两种状态复用同一组件）。

---

## 7. 常见业务错误

| 场景 | code | 后端返回 msg | 前端建议处理 |
|---|---|---|---|
| AI 调用额度不足 | 30200 | "AI 额度不足，请充值或明日再来" | 全局拦截，弹充值引导（见第 5 节） |
| 兑换码为空 | 30001 | "兑换码不能为空" | 提交前前端校验 |
| 兑换码不存在 | 30201 | "兑换码不存在" | 输入框下方红字提示 |
| 兑换码已使用 | 30201 | "兑换码已使用或已禁用" / "兑换码已被使用" | 红字提示 |
| 兑换码已过期 | 30201 | "兑换码已过期" | 红字提示 |
| 未登录/Token 失效 | 30007 | "未登录或登录已失效，请重新登录" | 跳登录页（全局已有逻辑） |

---

## 8. UI/UX 建议

- **余额常驻**：建议在用户头像/个人中心入口旁展示小字余额（如 `额度 35`），点击进额度中心页，数据来自 `GET /quota/balance`，可在登录后或每次 AI 调用成功后刷新。
- **兑换码输入**：自动转大写、去空格；支持粘贴；提交中禁用按钮防重复兑换。
- **兑换成功反馈**：用动效突出 `+100`，同时更新余额数字。
- **流水空状态**："还没有额度记录，去体验一下 AI 功能吧"。
- **消耗感知**：AI 功能按钮旁可小字提示预计消耗（如"约消耗 1-5 点"），具体数值由产品定，后端按真实 token 结算。

---

## 9. 联调注意事项

1. **鉴权头格式**：必须是 `Authorization: Bearer <token>`，token 通过短信登录接口获取。
2. **Swagger 调试**：优先使用 `http://localhost:8088/swagger-ui/index.html`，分组选择"正式客户端-AI 额度"。
3. **数据库前置**：联调前需先执行 `database/mysql/ida_v5_billing.sql` 和 `database/mysql/ida_v6_redeem.sql`。
4. **造测试兑换码**：测试期可直接往 `ida_redeem_code` 表 INSERT（示例 SQL 见 `ida_v6_redeem.sql` 末尾注释），管理后台的生成接口在 M1.3 提供。
5. **每日免费额度是懒发放**：新用户注册后 `balance` 为 0 属正常，首次调用任意 AI 功能时会自动 +10；不要误判为 bug。
6. **本模块无长耗时接口**：超时用默认即可（AI 功能接口的长耗时不属于本模块）。

---

## 10. 附录：完整时序示例

```text
1. 用户进入额度中心页
   GET /api/v1/app/quota/balance
   ← data: { "balance": 0, "totalQuota": 0, "memberLevel": "FREE", "lastDailyGrantDate": null }

2. 用户首次使用 AI 功能（如生成模拟面试），后端自动发放当日免费额度
   POST /api/v1/app/mock-interview
   ← 成功；流水新增 daily_grant +10、consume -N

3. 用户回到额度中心刷新
   GET /api/v1/app/quota/balance
   ← data: { "balance": 7, "totalQuota": 10, ... }

4. 额度用完后再次使用 AI 功能
   POST /api/v1/app/mock-interview
   ← code: 30200 → 前端全局弹窗"额度不足"

5. 用户输入兑换码充值
   POST /api/v1/app/quota/redeem
   { "code": "A1B2C3D4E5F6A7B8C9D0E1F2" }
   ← data: { "points": 100, "balanceAfter": 100 }

6. 前端刷新余额与流水
   GET /api/v1/app/quota/balance
   GET /api/v1/app/quota/transactions?limit=20
```
