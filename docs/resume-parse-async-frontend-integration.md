# 简历异步解析接口 · 前端对接说明

面向 **正式客户端**（桌面 / 手机 App），进程 `ida-app-server`，API 前缀 `/api/v1/app`。

本文档描述**新版异步**简历上传解析接口（`POST /resume/upload-parse/tasks` + 轮询）。
旧同步接口 `POST /resume/upload-parse` 仍保留可用，二者返回的解析结果结构完全一致；
建议新接入或改造时切换到异步版——同步版在大模型解析期间会长时间挂起 HTTP 连接，容易触发网关/浏览器超时。

---

## 1. 流程总览

```
① POST /resume/upload-parse/tasks（multipart 上传文件）
        ↓ 立即返回（毫秒级），data = taskId
② GET  /resume/upload-parse/tasks/{taskId}   ← 每 2 秒轮询一次
        ↓ status = RUNNING：继续轮询
        ↓ status = SUCCESS：data.result 即解析结果（与旧同步接口 data 结构相同）
        ↓ status = FAILED：展示 data.errorMessage，允许用户重试
```

典型耗时：小文件（几百 KB）约 5~30 秒，取决于大模型响应速度。

---

## 2. 提交解析任务

`POST /api/v1/app/resume/upload-parse/tasks`

- 请求头：`Authorization: Bearer <token>`（同其他 app 接口）
- 请求体：`multipart/form-data`，字段名 **`file`**（单个文件）

### 文件约束（建议前端上传前自检，直接给用户提示，不要等接口报错）

| 约束 | 值 | 超限表现 |
|---|---|---|
| 格式 | 仅 `.pdf` / `.docx` | FAILED，提示「仅支持 .pdf 或 .docx 格式」 |
| 大小（业务上限） | ≤ **15MB** | FAILED 或提交即拒绝，提示「文件过大，请上传不超过 15MB 的简历」 |
| 大小（HTTP 硬上限） | ≤ 30MB | 请求直接被拒（非 `ResponseDTO` 结构），前端按网络/413 类错误兜底处理 |
| 扫描件 PDF | 不支持 | FAILED，提示「未能从 PDF 提取文字，可能是扫描版 PDF」 |

### 响应

```json
{
  "code": 0,
  "ok": true,
  "data": "e61be340-6e94-4311-a397-3243fbcf2b87"
}
```

`data` 就是 **taskId**（字符串），保存它用于轮询。

提交时即可能同步失败的场景（不返回 taskId，直接 `code != 0`）：文件为空、超过 15MB。

---

## 3. 轮询任务状态

`GET /api/v1/app/resume/upload-parse/tasks/{taskId}`

### 响应 `data` 结构

```json
{
  "code": 0,
  "ok": true,
  "data": {
    "taskId": "e61be340-6e94-4311-a397-3243fbcf2b87",
    "status": "SUCCESS",
    "createdAt": 1758600000000,
    "updatedAt": 1758600018000,
    "result": { "draft": { "...": "..." } },
    "errorMessage": null
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | string | 任务 ID |
| `status` | string | `RUNNING`（处理中）/ `SUCCESS`（成功）/ `FAILED`（失败） |
| `createdAt` / `updatedAt` | number | Epoch 毫秒时间戳 |
| `result` | object \| null | **仅 SUCCESS 时有值**，结构与旧同步接口 `POST /resume/upload-parse` 的 `data` **完全相同**（含 `draft`、`ossSignedUrl`、`parseNotes` 等），现有渲染逻辑可直接复用 |
| `errorMessage` | string \| null | **仅 FAILED 时有值**，用户友好文案，可直接 toast 展示 |

### status 流转

```
RUNNING ──→ SUCCESS（result 有值，errorMessage 为 null）
        ──→ FAILED （result 为 null，errorMessage 有值）
```

终态（SUCCESS/FAILED）后不再变化，停止轮询。

### 轮询策略建议

- 间隔 **2 秒**；
- 整体超时建议 **120 秒**，超时后提示「解析时间较长，请稍后在解析记录中查看」或引导重试；
- 任务结果在服务端保留 **30 分钟**，超时后再轮询会收到业务错误「任务不存在、已过期或无权访问」（code `30100`）——收到该错误时引导用户重新上传即可；
- 页面切换/刷新后可凭 taskId 继续轮询，无需重新上传（30 分钟内有效）。

---

## 4. 失败文案（FAILED 时 errorMessage 可能出现）

| errorMessage | 建议前端动作 |
|---|---|
| 文件过大，请上传不超过 15MB 的简历 | 提示压缩/转换后重试 |
| 仅支持 .pdf 或 .docx 格式 | 提示转换格式 |
| 未能从 PDF 提取文字，可能是扫描版 PDF… | 引导使用可复制文字的 PDF 或 Word |
| AI 额度不足，请充值或明日再来 | 引导充值/兑换码入口 |
| 系统异常，请稍后重试 | 提供重试按钮 |

以上文案均为服务端返回的最终用户文案，**直接展示，不要自行包装或替换**。

---

## 5. 与旧同步接口的关系

| | 旧：`POST /resume/upload-parse` | 新：`POST /resume/upload-parse/tasks` + 轮询 |
|---|---|---|
| 连接占用 | 全程挂起（可达 60s+） | 提交毫秒级返回 |
| 结果结构 | `data` = 解析结果 | `data.result` = 同样的解析结果 |
| 适用 | 保留兼容，不推荐新用 | 推荐 |

两个接口可以并存灰度：先按 taskId 轮询接入新链路，异常时回退旧接口；稳定后旧接口仅作备用。

---

## 6. 本地联调

- 服务：`ida-app-server`，默认 `http://localhost:8088`
- Knife4j：`http://localhost:8088/doc.html`，分组选「正式客户端」（可在线调试这两个接口）
- 测试账号与测试简历文件：见 `docs/test-resume.md`
