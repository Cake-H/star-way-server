# 管理后台前端对接文档

## 1. 模块概述

管理后台是**独立进程**（`ida-admin-server`，默认端口 **8090**），与 C 端 API（8088）完全隔离，包含：

- 管理员账号密码登录 / 退出
- 用户管理：分页列表（手机号脱敏）、用户详情（含额度与用量汇总）、手动调整额度
- 用量看板：按天趋势、按业务占比、消耗 Top 用户榜
- 兑换码管理：批量生成、分页列表、禁用
- 网申站点管理：分页列表、新建、编辑、上下架

> **安全须知：** 后台接口拥有调额、禁用兑换码等敏感能力，生产环境务必仅在内网/VPN 可达；
> 所有接口（除登录与健康检查）均需要 **管理员 Bearer Token**。

---

## 2. 基础信息

| 项 | 值 |
|---|---|
| 基础路径 | `http://localhost:8090/api/v1/admin` |
| 鉴权方式 | `Authorization: Bearer <adminToken>`（登录接口获取） |
| Token 有效期 | **12 小时**（比 C 端短，过期后需重新登录） |
| Content-Type | `application/json` |
| 响应包装 | 统一返回 `ResponseDTO<T>`，业务数据在 `data` 字段 |

### 2.1 通用响应结构

```json
{
  "code": 0,
  "ok": true,
  "success": true,
  "msg": "操作成功",
  "data": { ... }
}
```

- `code == 0` 表示成功；非 0 时 `msg` 携带中文提示，直接展示即可。
- 未登录/Token 失效时返回 `30007`（"未登录或登录已失效，请重新登录"），前端拦截后跳登录页。

### 2.2 分页结构（所有列表接口统一）

```json
{
  "records": [ ... ],
  "total": 137,
  "page": 1,
  "size": 20
}
```

请求参数统一为 `page`（从 1 开始，默认 1）、`size`（默认 20，上限 100）。

---

## 3. 认证流程

```
1. POST /auth/login {username, password}
   ← data: { token, name, role }
2. 前端存储 token（localStorage/sessionStorage）
3. 后续所有请求携带 Authorization: Bearer <token>
4. 收到 30007 → 清空 token，跳登录页
5. 退出：POST /auth/logout（服务端作废 token），再清本地 token
```

### 3.1 登录

```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "Admin@123456"
}
```

**响应 `data`**：

```json
{
  "token": "9f8e7d6c5b4a...",
  "name": "系统管理员",
  "role": "ADMIN"
}
```

**登录失败场景**（前端按 `msg` 提示即可）：

| 场景 | msg | 建议 |
|---|---|---|
| 账号不存在 / 密码错误 | "用户名或密码错误" | 同一提示是**故意的**（防用户名枚举），不要区分提示 |
| 连续失败 ≥5 次 | "登录失败次数过多，账号已临时锁定，请稍后再试" | 提示 10 分钟后重试，可禁用登录按钮倒计时 |
| 账号被禁用 | "账号已被禁用，请联系超级管理员" | 直接展示 |

### 3.2 退出登录

```http
POST /api/v1/admin/auth/logout
Authorization: Bearer <token>
```

---

## 4. 接口清单

### 4.1 用户管理

#### 4.1.1 用户分页列表

```http
GET /api/v1/admin/users?page=1&size=20&keyword=138&status=1
Authorization: Bearer <token>
```

| Query 参数 | 必填 | 说明 |
|---|---|---|
| `page` / `size` | 否 | 分页，size 上限 100 |
| `keyword` | 否 | 手机号模糊匹配；纯数字时同时按用户 ID 精确匹配 |
| `status` | 否 | `1`=正常 `0`=禁用，不传查全部 |

**响应 `data.records[]`**：

```json
{
  "id": 9,
  "phone": "138****5678",
  "status": 1,
  "balance": 35,
  "memberLevel": "FREE",
  "createdAt": "2026-07-01T10:00:00"
}
```

> `phone` 已由后端脱敏（前 3 后 4），**前端不需要也不应尝试还原**。
> `balance` 可为负数（欠费），建议负数红色展示。

#### 4.1.2 用户详情

```http
GET /api/v1/admin/users/{userId}
Authorization: Bearer <token>
```

**响应 `data`**：

```json
{
  "id": 9,
  "phone": "138****5678",
  "status": 1,
  "balance": 35,
  "totalQuota": 120,
  "memberLevel": "FREE",
  "lastDailyGrantDate": "2026-07-21",
  "resumeCount": 3,
  "recent30dCallCount": 12,
  "recent30dTotalTokens": 34567,
  "recent30dCostPoints": 35,
  "createdAt": "2026-07-01T10:00:00"
}
```

| 字段 | 说明 |
|---|---|
| `resumeCount` | 该用户简历数量 |
| `recent30dCallCount` / `recent30dTotalTokens` / `recent30dCostPoints` | 近 30 天 AI 调用次数 / 消耗 token / 消耗额度点数 |

#### 4.1.3 手动调整额度

```http
POST /api/v1/admin/users/{userId}/quota-adjust
Authorization: Bearer <token>
Content-Type: application/json

{
  "points": 50,
  "remark": "种子用户赠送"
}
```

| 字段 | 校验 | 说明 |
|---|---|---|
| `points` | 必填，-100000 ~ 100000，**不能为 0** | 正数=充值，负数=扣减 |
| `remark` | 必填，≤200 字 | 调整原因，写入流水审计（操作管理员 ID 由后端自动追加） |

**响应 `data`**：`{ "userId": 9, "points": 50, "balanceAfter": 85 }`

> 建议前端做**二次确认弹窗**（尤其负值扣减），成功后刷新详情与列表。

---

### 4.2 用量看板

三个接口的 `from` / `to` 均为可选（`yyyy-MM-dd`），缺省统计**近 30 天**；跨度上限 366 天。

#### 4.2.1 按天聚合（趋势图）

```http
GET /api/v1/admin/usage/daily?from=2026-07-01&to=2026-07-21
```

**响应 `data[]`**：

```json
{ "day": "2026-07-21", "callCount": 58, "totalTokens": 102300, "costPoints": 103 }
```

> 用于折线图：X 轴 `day`，Y 轴可选 `callCount` / `totalTokens` / `costPoints`。

#### 4.2.2 按业务类型聚合（占比图）

```http
GET /api/v1/admin/usage/by-biz?from=2026-07-01&to=2026-07-21
```

**响应 `data[]`**（按 costPoints 降序）：

```json
{ "bizType": "plugin_autofill", "callCount": 120, "totalTokens": 240000, "costPoints": 240 }
```

`bizType` 中文映射：`resume_parse`=简历解析、`plugin_autofill`=一键填表、`interview_summarize`=面试总结、`mock_interview_generate`=模拟面试。

#### 4.2.3 消耗 Top 用户榜

```http
GET /api/v1/admin/usage/top-users?from=2026-07-01&to=2026-07-21&limit=20
```

`limit` 上限 100。**响应 `data[]`**（按 costPoints 降序）：

```json
{ "userId": 9, "callCount": 12, "totalTokens": 34567, "costPoints": 35 }
```

> 点击某行可跳「用户详情」页（`GET /users/{userId}`）。

---

### 4.3 兑换码管理

#### 4.3.1 批量生成兑换码 ⚠️

```http
POST /api/v1/admin/redeem-codes/generate
Authorization: Bearer <token>
Content-Type: application/json

{
  "count": 10,
  "points": 100,
  "expireTime": "2026-09-01T00:00:00",
  "remark": "seed-batch-001"
}
```

| 字段 | 校验 | 说明 |
|---|---|---|
| `count` | 必填，1-1000 | 生成数量 |
| `points` | 必填，1-100000 | 每张点数 |
| `expireTime` | 否 | 过期时间（`yyyy-MM-ddTHH:mm:ss`），**不传=永不过期** |
| `remark` | 否，≤200 字 | 批次备注 |

**响应 `data`**：生成的兑换码数组（结构同 4.3.2 列表项）。

> ⚠️ **明文码仅此一次完整返回**，不会再次展示。前端必须：生成成功弹窗展示全部码 + 提供**一键复制/导出 CSV**，并提示"请立即保存，关闭后无法再次查看完整码"。

#### 4.3.2 兑换码分页列表

```http
GET /api/v1/admin/redeem-codes?page=1&size=20&status=ACTIVE
```

`status` 可选：`ACTIVE` / `USED` / `DISABLED`（不传查全部）。

**响应 `data.records[]`**：

```json
{
  "id": 100,
  "code": "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5",
  "points": 100,
  "status": "ACTIVE",
  "usedBy": null,
  "usedAt": null,
  "expireTime": "2026-09-01T00:00:00",
  "remark": "seed-batch-001",
  "createdAt": "2026-07-21T10:00:00"
}
```

#### 4.3.3 禁用兑换码

```http
POST /api/v1/admin/redeem-codes/{codeId}/disable
Authorization: Bearer <token>
```

- 仅 `ACTIVE` 状态可禁用；已使用/已禁用的返回 30100（"兑换码不存在或已被使用/禁用"）。
- 建议二次确认弹窗。

---

### 4.4 网申站点管理

#### 4.4.1 站点分页列表

```http
GET /api/v1/admin/apply-sites?page=1&size=20&category=互联网&status=1
```

**响应 `data.records[]`**：

```json
{
  "id": 1,
  "name": "腾讯校招",
  "logoUrl": "https://cdn.example.com/logo/tencent.png",
  "url": "https://join.qq.com",
  "category": "互联网",
  "supportAutofill": 0,
  "sortOrder": 30,
  "status": 1,
  "remark": null,
  "updatedAt": "2026-07-21T10:00:00"
}
```

#### 4.4.2 新建站点

```http
POST /api/v1/admin/apply-sites
Content-Type: application/json

{
  "name": "美团校招",
  "url": "https://campus.meituan.com",
  "logoUrl": "https://cdn.example.com/logo/meituan.png",
  "category": "互联网",
  "supportAutofill": 0,
  "sortOrder": 33,
  "remark": null
}
```

| 字段 | 校验 | 说明 |
|---|---|---|
| `name` | 必填，≤128 字 | 站点名称 |
| `url` | 必填，**必须 http/https 合法地址** | 网申入口 |
| `logoUrl` | 否，≤512 字 | Logo；为空时前端用首字母占位图 |
| `category` | 必填，≤32 字 | 建议下拉：互联网/国企/银行/外企/快消/综合招聘/实习/ATS系统 |
| `supportAutofill` | 必填，`0` 或 `1` | 是否支持插件一键填表 |
| `sortOrder` | 必填，整数 | 越小越靠前 |
| `remark` | 否 | 内部备注 |

**响应 `data`**：新站点 ID。新建默认**上架**。

#### 4.4.3 更新站点

```http
PUT /api/v1/admin/apply-sites/{siteId}
```

Body 同新建。**注意：此接口不变更上下架状态**，`status` 由 toggle 接口单独管理。

#### 4.4.4 上下架切换

```http
POST /api/v1/admin/apply-sites/{siteId}/toggle
```

上架（1）↔ 下架（0）翻转。下架后 C 端「网申网站列表」不再展示该站点。

---

## 5. 页面建议

```
登录页 /login
布局（登录后）：左侧导航
├── 仪表盘（用量看板）
│     ├── 按天趋势折线图（/usage/daily）
│     ├── 业务占比饼图（/usage/by-biz）
│     └── 消耗 Top 用户表格（/usage/top-users）→ 点行跳用户详情
├── 用户管理
│     ├── 列表页：搜索框(keyword) + 状态筛选 + 余额列(负数红色)
│     └── 详情抽屉/页：基本信息 + 近30天用量 + [调整额度] 按钮
├── 兑换码管理
│     ├── [批量生成] 按钮 → 表单弹窗 → 成功后展示码列表(可复制/导出)
│     └── 列表页：状态筛选 + [禁用] 操作（仅 ACTIVE 行可用）
└── 网申站点管理
      └── 列表页：分类筛选 + [新建] + 行内 [编辑] [上架/下架] 开关
```

### 关键交互细节

1. **调整额度**：弹窗内显示当前余额，输入正负点数 + 必填原因 → 二次确认（负数红色警示）→ 成功后 toast 新余额。
2. **生成兑换码**：成功弹窗用等宽字体逐行列出码，提供"复制全部"和"导出 CSV"按钮，关闭前二次确认"已保存"。
3. **状态标签**：用户 status（正常/禁用）、兑换码 status（ACTIVE 绿/USED 灰/DISABLED 红）、站点 status（上架/下架）。
4. **欠费展示**：balance < 0 时显示 `欠费 12`，红色。

---

## 6. 常见业务错误

| 场景 | code | msg | 前端建议 |
|---|---|---|---|
| Token 失效/未登录 | 30007 | "未登录或登录已失效，请重新登录" | 全局拦截跳登录页 |
| 登录失败 | 30100 | "用户名或密码错误" | 登录页内联提示 |
| 登录锁定 | 30100 | "登录失败次数过多，账号已临时锁定..." | 提示稍后重试 |
| 参数校验失败 | 30001 | 如 "调整原因不能为空"、"站点 URL 必须是合法的 https 地址" | 表单内联提示（msg 可直接展示） |
| 调整点数为 0 | 30100 | "调整点数不能为 0" | 提交前前端校验 |
| 用户/站点不存在 | 30100 | "用户不存在" / "站点不存在" | toast 后刷新列表 |
| 禁用非 ACTIVE 兑换码 | 30100 | "兑换码不存在或已被使用/禁用" | toast 并刷新该行状态 |

---

## 7. 联调注意事项

1. **数据库前置**：需依次执行 `ida_v5_billing.sql`、`ida_v6_redeem.sql`、`ida_v7_admin.sql`。
2. **初始管理员**：`admin / Admin@123456`（bcrypt 哈希已入库）。**仅限本地联调，测试/生产环境必须第一时间改密**（直接 UPDATE `ida_admin_account.password_hash` 为新密码的 BCrypt 值）。
3. **端口隔离**：后台 8090、C 端 8088、插件端 8089，前端 dev 代理注意区分。
4. **Token 12 小时过期**：无刷新机制，过期即跳登录页重新登录。
5. **Swagger 调试**：`http://localhost:8090/swagger-ui/index.html`；先在 `/auth/login` 拿到 token，点右上角 Authorize 填入 `Bearer <token>`。
6. **手机号天然脱敏**：列表/详情接口返回的就是脱敏值，前端不要做"查看完整手机号"功能（后端不提供）。
7. **时间格式**：请求中的 `expireTime` 用 ISO 本地时间 `yyyy-MM-ddTHH:mm:ss`；`from/to` 用 `yyyy-MM-dd`。

---

## 8. 附录：完整时序示例

```text
1. 管理员登录
   POST /api/v1/admin/auth/login
   { "username": "admin", "password": "Admin@123456" }
   ← data: { "token": "t-xxx", "name": "系统管理员", "role": "ADMIN" }

2. 进入仪表盘
   GET /api/v1/admin/usage/daily          ← 近30天趋势
   GET /api/v1/admin/usage/by-biz         ← 业务占比
   GET /api/v1/admin/usage/top-users      ← Top 榜，发现 userId=9 消耗异常

3. 查看该用户并调额
   GET /api/v1/admin/users/9              ← balance=-5（欠费）
   POST /api/v1/admin/users/9/quota-adjust
   { "points": 50, "remark": "种子用户补偿" }
   ← data: { "userId": 9, "points": 50, "balanceAfter": 45 }

4. 生成一批兑换码给运营
   POST /api/v1/admin/redeem-codes/generate
   { "count": 10, "points": 100, "remark": "seed-batch-002" }
   ← data: [10 张明码] → 前端导出 CSV

5. 新增网申站点
   POST /api/v1/admin/apply-sites
   { "name": "美团校招", "url": "https://campus.meituan.com",
     "category": "互联网", "supportAutofill": 0, "sortOrder": 33 }
   ← data: 12

6. 退出
   POST /api/v1/admin/auth/logout
```
