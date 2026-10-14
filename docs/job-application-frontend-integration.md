# 投递记录模块 · 前端对接说明

面向 **正式客户端**（桌面 / 手机 App），**不**走插件端 `/api/v1/plugin`。

功能定位：把 App 当投递笔记本用，记录「投了哪家公司、哪个岗位、进度到哪一步」，表格化展示，支持组合筛选与编辑。

---

## 1. 环境与 Base URL

| 项 | 值 |
|----|-----|
| 进程 | `ida-app-server`（与插件进程分离） |
| 默认端口 | `8088`（环境变量 `APP_SERVER_PORT` 可覆盖） |
| API 前缀 | `/api/v1/app` |
| 投递记录 Base | **`/api/v1/app/job-application`** |
| 完整示例 | `http://localhost:8088/api/v1/app/job-application` |

本地联调可用 Knife4j：`http://localhost:8088/doc.html`，分组选 **「正式客户端-投递记录」**。

---

## 2. 鉴权

与面试复盘等模块完全一致：

1. 短信登录：`POST /api/v1/app/auth/sms/login`，保存响应 `data.token`
2. 后续请求头：

```http
Authorization: Bearer <token>
```

本模块全部接口要求登录。无有效身份时 HTTP **401**，前端应跳转登录。

本地开发环境也可用模拟号 `13800000000` / 验证码 `246810` 直接登录（`mock-code-enabled` 仅 dev 开启）。

---

## 3. 统一响应体 `ResponseDTO`

```json
{
  "code": 0,
  "ok": true,
  "success": true,
  "msg": "操作成功",
  "message": "操作成功",
  "data": { }
}
```

- **前端判断成功**：`code === 0`。
- 参数校验失败：`code = 30001`，`msg` 为字段提示（如「投递公司不能为空」）。
- 业务异常：`code = 30100`，`msg` 为业务文案（如「投递日期格式应为 YYYY-MM-DD」「投递记录不存在或无权访问」）。
- 更新 / 删除接口成功时 `data` 为 `null`，看 `code` 即可。

---

## 4. 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/job-application` | 投递记录列表（支持组合筛选） |
| GET | `/job-application/progress-options` | 投递进度下拉选项 |
| POST | `/job-application` | 新建投递记录 |
| PUT | `/job-application/{applicationId}` | 更新投递记录（部分字段） |
| DELETE | `/job-application/{applicationId}` | 删除投递记录（软删） |

路径均相对于 Base：`/api/v1/app`。

---

## 5. 数据模型

### 5.1 列表项 `JobApplicationListItemVO`

`GET /job-application` → `data` 为 **数组**（**无分页**，个人投递量为百级，返回全量，前端自行渲染表格）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `applicationId` | number | 记录 id（更新/删除路径参数用它） |
| `companyName` | string | 投递公司 |
| `position` | string | 投递岗位 |
| `applyDate` | string \| null | 投递日期 **`YYYY-MM-DD`** |
| `progress` | string | 进度 code，见 5.2 |
| `progressLabel` | string | 进度中文文案（如「笔试」），可直接展示 |
| `applyUrl` | string | 网申地址，可能为空串 |
| `source` | string | 来源：`MANUAL`（手动记录）/ `PLUGIN`（插件识别入库，预留） |
| `remark` | string | 备注，可能为空串 |
| `createdAt` / `updatedAt` | string | ISO 本地时间 |

**默认排序**：`applyDate` 降序，其次 `createdAt` 降序（最近投递在前）。

**数据约定**：一行 = 一个岗位的一次投递。同一公司投多个岗位就是多行，前端如需「按公司分组」视图可自行 group。

### 5.2 投递进度枚举

`progress` 字段只接受以下 code（大小写敏感）：

| code | label |
|------|-------|
| `APPLIED` | 已投递 |
| `ASSESSMENT` | 测评 |
| `SCREENING` | 初筛 |
| `RESCREEN` | 复筛 |
| `WRITTEN_TEST` | 笔试 |
| `INTERVIEW_1` | 一面 |
| `INTERVIEW_2` | 二面 |
| `FINAL_INTERVIEW` | 终面 |
| `OFFER` | offer |
| `TERMINATED` | 终止 |

前端**不要硬编码**该列表，用 `GET /job-application/progress-options` 拉取（响应 `data` 为 `[{code, label}, ...]`，按流程顺序排列），下拉筛选和编辑表单共用。

---

## 6. 接口说明

### 6.1 列表 `GET /job-application`

查询参数**均可选、可自由组合**，均不传则返回当前用户全部记录：

| 参数 | 匹配方式 | 说明 |
|------|----------|------|
| `companyName` | 模糊（`LIKE %x%`） | 投递公司 |
| `position` | 模糊（`LIKE %x%`） | 投递岗位 |
| `progress` | 精确 | 进度 code，非法值报「投递进度不合法」 |
| `applyDateStart` | `apply_date >=` | `YYYY-MM-DD` |
| `applyDateEnd` | `apply_date <=` | `YYYY-MM-DD`，与 start 组成闭区间 |

示例：

```http
GET /api/v1/app/job-application?position=%E5%90%8E%E7%AB%AF&progress=APPLIED&applyDateStart=2026-10-01
```

（即「岗位含『后端』且进度为已投递且 10 月以后」。注意查询串中的中文需 URL 编码，浏览器/Axios 会自动处理。）

响应示例（节选一条）：

```json
{
  "code": 0,
  "data": [
    {
      "applicationId": 2,
      "companyName": "字节跳动",
      "position": "后端开发工程师",
      "applyDate": "2026-10-01",
      "progress": "APPLIED",
      "progressLabel": "已投递",
      "applyUrl": "https://jobs.bytedance.com/campus/position/123",
      "source": "MANUAL",
      "remark": "校招提前批",
      "createdAt": "2026-10-14T12:18:20.14",
      "updatedAt": "2026-10-14T12:18:20.14"
    }
  ]
}
```

### 6.2 新建 `POST /job-application`

`Content-Type: application/json`

```json
{
  "companyName": "字节跳动",
  "position": "后端开发工程师",
  "applyDate": "2026-10-01",
  "progress": "APPLIED",
  "applyUrl": "https://jobs.bytedance.com/campus/position/123",
  "remark": "校招提前批"
}
```

| 字段 | 必填 | 约束 / 缺省行为 |
|------|------|----------------|
| `companyName` | 是 | 非空白，最长 128 字 |
| `position` | 是 | 非空白，最长 128 字 |
| `applyDate` | 否 | `YYYY-MM-DD`；**不传默认当天** |
| `progress` | 否 | 合法 code；**不传默认 `APPLIED`** |
| `applyUrl` | 否 | 最长 512 字 |
| `remark` | 否 | 最长 1024 字 |

响应 `data`：

```json
{ "applicationId": 2 }
```

新建来源固定为 `MANUAL`，前端无需也无法指定 `source`。

### 6.3 更新 `PUT /job-application/{applicationId}`

**部分更新（PATCH 语义）**：请求体里 **为 `null` 的字段（含未传的字段）表示不修改**。

```json
{
  "progress": "INTERVIEW_1",
  "remark": "一面约10-20"
}
```

- `companyName` / `position`：仅当非 `null` 且**有非空白字符**时才更新（不允许改成空）。
- `applyUrl` / `remark`：传 `null` 不更新；传 `""`（或纯空白）会清空（存为空串）。
- `applyDate`：传 `""` 清空为 `null`；否则必须 `YYYY-MM-DD`。
- `progress` 传了就必须是合法 code，否则报「投递进度不合法」。
- 成功 `data` 为 `null`。

**进度变更就是走这个接口**，只传 `{ "progress": "OFFER" }` 即可。

### 6.4 删除 `DELETE /job-application/{applicationId}`

软删（数据保留，列表不再返回）。成功 `data` 为 `null`。

### 6.5 错误场景速查

| 场景 | code | msg |
|------|------|-----|
| 未登录 | HTTP 401 | （Spring 默认错误体） |
| 必填缺失 / 超长 | 30001 | 如「投递公司不能为空」 |
| 日期格式错 | 30100 | 「投递日期格式应为 YYYY-MM-DD」 |
| 进度非法 | 30100 | 「投递进度不合法」 |
| 记录不存在 / 改删他人记录 | 30100 | 「投递记录不存在或无权访问」 |

---

## 7. 推荐页面与流程

### 7.1 表格页

1. 进页面先 `GET /job-application`（带当前筛选条件）+ `GET /job-application/progress-options`。
2. 表格列建议：投递日期、公司、岗位、进度（展示 `progressLabel`，配颜色 tag）、网申地址（`applyUrl` 渲染为可点击链接，空串则不渲染）、备注、操作（编辑/删除）。
3. 筛选区：公司输入框、岗位输入框、进度下拉（options 接口）、日期区间选择器；任一变化即带参重新请求，全部为服务端筛选。
4. `source === 'PLUGIN'` 的记录可以加个小标识（如「插件同步」），`MANUAL` 不展示。

### 7.2 新建 / 编辑弹窗

- 新建：`applyDate` 控件默认填今天；`progress` 默认选 `APPLIED`。
- 编辑：用当前行数据回填，提交时**全量传表单值**即可（后端按非 null 更新，语义兼容）。
- 保存成功后重新拉列表（或就地更新该行）。

### 7.3 进度快速变更

表格行内放一个进度下拉，变更即 `PUT /job-application/{id}`，body 只带 `progress`。

---

## 8. Axios 示例

```typescript
const base = '/api/v1/app/job-application';

// 列表（组合筛选）
async function listApplications(query: {
  companyName?: string;
  position?: string;
  progress?: string;
  applyDateStart?: string;
  applyDateEnd?: string;
}) {
  const { data } = await axios.get(base, {
    params: query, // axios 自动做 URL 编码
    headers: { Authorization: `Bearer ${token}` },
  });
  if (data.code !== 0) throw new Error(data.msg);
  return data.data as JobApplicationListItemVO[];
}

// 新建
async function createApplication(body: JobApplicationCreateRequest) {
  const { data } = await axios.post(base, body, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (data.code !== 0) throw new Error(data.msg);
  return data.data.applicationId as number;
}

// 进度变更（部分更新）
async function updateProgress(applicationId: number, progress: string) {
  const { data } = await axios.put(
    `${base}/${applicationId}`,
    { progress },
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (data.code !== 0) throw new Error(data.msg);
}

// 删除
async function deleteApplication(applicationId: number) {
  const { data } = await axios.delete(`${base}/${applicationId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (data.code !== 0) throw new Error(data.msg);
}
```

---

## 9. 常见问题

**Q：列表为什么不分页？**  
A：个人投递记录为百级数据量，与面试复盘一致返回全量；筛选在服务端做。若后续量大了会加 `PageVO` 分页，届时另行通告。

**Q：同一家公司投多个岗位怎么记？**  
A：一个岗位一行，各自独立维护进度和备注；需要按公司聚合视图时前端自行 group。

**Q：更新时如何把备注清空？**  
A：`PUT` 传 `"remark": ""`（空串）。不传该字段则保持不变。

**Q：`applyDate` 可以为空吗？**  
A：新建不传默认当天；更新传 `""` 可清空为 `null`。

**Q：插件端自动入库的记录在哪看？**  
A：就是这张表（`source = PLUGIN`），同一列表接口返回，前端无需特殊请求。（插件识别功能上线后生效。）

---

## 10. 变更记录

| 版本 | 说明 |
|------|------|
| V1 | 列表（公司/岗位/日期/进度组合筛选）、进度选项、新建、部分更新、软删；无分页 |
