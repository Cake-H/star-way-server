# 用户反馈模块 · 前端对接说明

面向 **正式客户端**（桌面 / 手机 App），**不**走插件端 `/api/v1/plugin`。

功能定位：App 内「意见反馈」入口，用户提交文字反馈（可选联系邮箱），并可附带截图/录屏（图片或视频附件）；用户可在「我的反馈」查看处理进度与管理员备注。

---

## 1. 环境与 Base URL

| 项 | 值 |
|----|-----|
| 进程 | `ida-app-server`（与插件进程分离） |
| 默认端口 | `8088`（环境变量 `APP_SERVER_PORT` 可覆盖） |
| API 前缀 | `/api/v1/app` |
| 用户反馈 Base | **`/api/v1/app/feedback`** |
| 完整示例 | `http://localhost:8088/api/v1/app/feedback` |

本地联调可用 Knife4j：`http://localhost:8088/doc.html`，分组选 **「正式客户端-用户反馈」**。

---

## 2. 鉴权

与投递记录等模块完全一致：

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
- 参数校验失败：`code = 30001`，`msg` 为字段提示（如「反馈内容不能为空」）。
- 业务异常：`code = 30100`，`msg` 为业务文案（如「附件最多上传 6 个」）。
- 各接口 `data` 结构见下文。

---

## 4. 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/feedback` | 提交反馈（multipart/form-data，可带附件） |
| GET | `/feedback/my` | 我的反馈列表（无分页，按提交时间倒序） |

路径均相对于 Base：`/api/v1/app`。

---

## 5. 数据模型

### 5.1 列表项 `AppFeedbackListItemVO`

`GET /feedback/my` → `data` 为 **数组**（**无分页**，个人反馈量为百级，返回全量，与投递记录一致）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `feedbackId` | string | 反馈 id（Long 序列化为字符串，防 JS 精度丢失，回传时用字符串即可） |
| `content` | string | 反馈内容 |
| `attachments` | array | 附件列表，见 5.2；无附件为空数组 `[]` |
| `status` | string | 处理状态 code，见 5.3 |
| `statusLabel` | string | 状态中文文案（如「处理中」），可直接展示 |
| `adminNote` | string | 管理员处理备注，未处理时为空串 |
| `createdAt` | string | ISO 本地时间 |

**默认排序**：`createdAt` 降序（最近提交在前）。

### 5.2 附件 `AppFeedbackAttachmentVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | string | **预签名下载 URL，有效期 1 小时**；OSS 未配置或签名失败时为空串，前端应做占位处理 |
| `fileName` | string | 原始文件名 |
| `mimeType` | string | 如 `image/png`、`video/mp4` |
| `size` | number | 字节数 |
| `mediaType` | string | `IMAGE` / `VIDEO`，决定用 `<img>` 还是 `<video>` 渲染 |

> **重要**：`url` 是 1 小时有效的预签名链接（Bucket 私有读，无公开直链）。拿到后**及时展示，不要缓存复用**；页面停留较久后链接可能过期，重新拉取列表即可换新。

### 5.3 处理状态枚举

`status` 字段只会出现以下 code（大小写敏感）：

| code | label |
|------|-------|
| `PENDING` | 待处理 |
| `PROCESSING` | 处理中 |
| `RESOLVED` | 已解决 |

提交后初始状态固定为 `PENDING`；状态流转由管理后台操作，前端只读展示 `statusLabel` 即可。

---

## 6. 接口说明

### 6.1 提交反馈 `POST /feedback`

`Content-Type: multipart/form-data`（含文件时必须用表单提交，不能发 JSON）。

| 表单字段 | 必填 | 约束 / 说明 |
|----------|------|----------------|
| `content` | 是 | 非空白，**最长 2000 字** |
| `contactEmail` | 否 | 合法邮箱格式，最长 128 字；用于管理员回复联系 |
| `attachments` | 否 | 图片/视频附件，**最多 6 个，单个 ≤ 50MB**；同名多文件字段；允许类型见下 |

附件允许的 MIME 类型（前端可按扩展名预校验）：

- 图片：`image/jpeg`、`image/png`、`image/webp`、`image/gif`（jpg / png / webp / gif）
- 视频：`video/mp4`、`video/quicktime`（mp4 / mov）

**curl 示例**：

```bash
curl -X POST "http://localhost:8088/api/v1/app/feedback" \
  -H "Authorization: Bearer <token>" \
  -F "content=导出简历时排版错乱，见附件截图" \
  -F "contactEmail=user@example.com" \
  -F "attachments=@/path/to/screenshot.png" \
  -F "attachments=@/path/to/recording.mp4"
```

响应 `data`：

```json
{ "feedbackId": "1" }
```

说明：

- 附件先传 OSS 再落库；无附件的纯文字反馈不依赖 OSS 配置，可直接提交。
- 提交成功后初始状态为 `PENDING`，前端可提示「已提交，我们会尽快处理」并刷新「我的反馈」。

### 6.2 我的反馈列表 `GET /feedback/my`

无查询参数，返回当前用户全部未删除反馈（按提交时间倒序）。

```http
GET /api/v1/app/feedback/my
Authorization: Bearer <token>
```

响应示例（节选一条）：

```json
{
  "code": 0,
  "data": [
    {
      "feedbackId": "1",
      "content": "导出简历时排版错乱，见附件截图",
      "attachments": [
        {
          "url": "https://bucket.oss-cn-xxx.aliyuncs.com/app/feedback/9/3f2a....png?Expires=...",
          "fileName": "screenshot.png",
          "mimeType": "image/png",
          "size": 204800,
          "mediaType": "IMAGE"
        }
      ],
      "status": "PENDING",
      "statusLabel": "待处理",
      "adminNote": "",
      "createdAt": "2026-10-21T12:18:20.14"
    }
  ]
}
```

### 6.3 错误场景速查

| 场景 | code | msg |
|------|------|-----|
| 未登录 | HTTP 401 | （Spring 默认错误体） |
| 内容为空 / 超 2000 字 | 30001 | 「反馈内容不能为空」/「反馈内容不能超过 2000 字」 |
| 邮箱格式错 / 超 128 字 | 30001 | 「联系邮箱格式不正确」/「联系邮箱不能超过 128 字」 |
| 附件超过 6 个 | 30100 | 「附件最多上传 6 个」 |
| 单个附件超 50MB | 30100 | 「单个附件不能超过 50MB」 |
| 附件类型不支持 | 30100 | 「附件仅支持图片（jpg/png/webp/gif）或视频（mp4/mov）」 |
| 服务端未配置 OSS（仅带附件时） | 30100 | 「未启用阿里云 OSS：请在配置中设置 ida.aliyun.oss.enabled=true 并填写 endpoint、bucket、AK/SK」 |

---

## 7. 推荐页面与流程

### 7.1 反馈提交弹窗 / 页

- 多行文本框（`content`，计数器 0/2000）+ 邮箱输入框（可选）+ 附件选择器（accept 限 `image/jpeg,image/png,image/webp,image/gif,video/mp4,video/quicktime`，多选，前端先校验数量 ≤6、单文件 ≤50MB，减少一次失败往返）。
- 提交中禁用按钮防重复提交；成功后 toast 并刷新列表。

### 7.2 我的反馈列表页

- 进页面 `GET /feedback/my` 一次拿全量。
- 每条卡片：内容、提交时间、状态 tag（`statusLabel` 直接展示，建议配色：待处理-灰 / 处理中-蓝 / 已解决-绿）、管理员备注（`adminNote` 非空时展示）、附件缩略（`mediaType=IMAGE` 用 `<img>`，`VIDEO` 用 `<video>` 或封面 + 点击播放，`url` 直接作为 src）。
- 附件 `url` 1 小时过期：下拉刷新 / 重新进入页面时重新拉列表即可，**不要**把 url 持久化。

---

## 8. Axios / FormData 示例

```typescript
const base = '/api/v1/app/feedback';

// 提交反馈（multipart）
async function submitFeedback(input: {
  content: string;
  contactEmail?: string;
  attachments?: File[];
}) {
  const form = new FormData();
  form.append('content', input.content);
  if (input.contactEmail) form.append('contactEmail', input.contactEmail);
  for (const f of input.attachments ?? []) {
    form.append('attachments', f); // 同名多文件字段
  }
  const { data } = await axios.post(base, form, {
    headers: { Authorization: `Bearer ${token}` },
    // ⚠️ 不要手动设置 Content-Type！浏览器/axios 会自动带上
    // multipart/form-data 及 boundary，手动设置会导致服务端解析失败。
  });
  if (data.code !== 0) throw new Error(data.msg);
  return data.data.feedbackId as string;
}

// 我的反馈列表
async function listMyFeedbacks() {
  const { data } = await axios.get(`${base}/my`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (data.code !== 0) throw new Error(data.msg);
  return data.data as AppFeedbackListItemVO[];
}
```

---

## 9. 常见问题

**Q：列表为什么不分页？**  
A：个人反馈为百级数据量，与投递记录、面试复盘一致返回全量；若后续量大了会加 `PageVO` 分页，届时另行通告。

**Q：附件 url 打开 403 / 过期了？**  
A：url 是 1 小时有效的 OSS 预签名链接，过期后重新调 `GET /feedback/my` 获取新链接即可；请勿缓存 url。

**Q：提交时不带附件可以吗？**  
A：可以，`attachments` 整个字段不传即可；纯文字反馈不要求服务端配置 OSS。

**Q：可以删除或修改已提交的反馈吗？**  
A：当前版本不提供用户侧修改/删除接口；处理进度由管理后台流转，前端只读展示。

**Q：`contactEmail` 和登录手机号什么关系？**  
A：互不影响。`contactEmail` 是用户**可选**留下的回复联系方式，留空也能提交。

---

## 10. 变更记录

| 版本 | 说明 |
|------|------|
| V1 | 提交反馈（multipart，附件 ≤6 个 / 单个 ≤50MB，jpg/png/webp/gif/mp4/mov）、我的反馈列表（无分页、倒序、状态与管理员备注回显、附件 1 小时预签名 URL） |
