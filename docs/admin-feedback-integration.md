# 管理后台 · 用户反馈对接文档

## 1. 模块概述

管理后台新增「用户反馈」模块：查看 APP 端用户提交的反馈（文字 + 图片/视频附件 + 可选联系邮箱），并流转处理状态、填写处理备注。

- 反馈分页列表（状态筛选 + 关键字搜索）
- 反馈详情（附件预签名 URL 预览）
- 更新处理状态（PENDING / PROCESSING / RESOLVED + 处理备注）

> 通用约定（登录、Token、分页结构、错误码等）见《管理后台前端对接文档》（`admin-frontend-integration.md`），本文只描述反馈模块本身。

---

## 2. 基础信息

| 项 | 值 |
|---|---|
| 基础路径 | `http://localhost:8090/api/v1/admin/feedback` |
| 鉴权方式 | `Authorization: Bearer <adminToken>`（`POST /api/v1/admin/auth/login` 获取，有效期 12 小时） |
| Content-Type | `application/json`（状态更新接口） |
| 响应包装 | 统一返回 `ResponseDTO<T>`，业务数据在 `data` 字段 |

分页请求参数统一为 `page`（从 1 开始，默认 1）、`size`（默认 20，上限 100）；分页响应结构：

```json
{
  "records": [ ... ],
  "total": 137,
  "page": 1,
  "size": 20
}
```

---

## 3. 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/feedback` | 反馈分页列表（status/keyword 筛选） |
| GET | `/feedback/{feedbackId}` | 反馈详情 |
| PUT | `/feedback/{feedbackId}/status` | 更新处理状态与处理备注 |

### 3.1 反馈分页列表

```http
GET /api/v1/admin/feedback?page=1&size=20&status=PENDING&keyword=排版
Authorization: Bearer <token>
```

| Query 参数 | 必填 | 说明 |
|---|---|---|
| `page` / `size` | 否 | 分页，size 上限 100 |
| `status` | 否 | 处理状态 code 精确匹配（见 4.2），不传查全部；非法值报「反馈状态不合法」 |
| `keyword` | 否 | 模糊匹配**反馈内容 / 联系邮箱**（`LIKE %x%`），不传查全部 |

**默认排序**：`createdAt` 降序（最新提交在前）。

**响应 `data.records[]`**（结构同详情，见 4.1）：

```json
{
  "id": 1,
  "userId": 9,
  "source": "APP",
  "contactEmail": "user@example.com",
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
  "createdAt": "2026-10-21T12:18:20.14",
  "updatedAt": "2026-10-21T12:18:20.14"
}
```

### 3.2 反馈详情

```http
GET /api/v1/admin/feedback/1
Authorization: Bearer <token>
```

响应 `data` 结构同列表项（见 4.1）。反馈不存在时返回 30100「反馈不存在」。

### 3.3 更新处理状态

```http
PUT /api/v1/admin/feedback/1/status
Authorization: Bearer <token>
Content-Type: application/json

{
  "status": "PROCESSING",
  "adminNote": "已转研发排查，预计本周修复"
}
```

| 字段 | 校验 | 说明 |
|---|---|---|
| `status` | 必填，非空白，合法 code（见 4.2） | 目标处理状态 |
| `adminNote` | 否，≤512 字 | 处理备注，会回显给提交用户 |

**`adminNote` 更新语义**（与投递记录部分更新一致）：

- **不传 / 传 `null`**：不修改原有备注；
- **传 `""`（或纯空白）**：清空备注（存为空串）。

成功时 `data` 为 `null`，看 `code` 即可。建议成功 toast 后刷新该行 / 详情。

> 操作管理员 ID 由后端自动记录到日志，前端无需传。

---

## 4. 数据模型

### 4.1 反馈 `AdminFeedbackVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 反馈 id（详情/状态更新路径参数用它；Long 序列化为字符串，防 JS 精度丢失） |
| `userId` | string | 提交用户 ID（Long 序列化为字符串），可结合「用户详情」接口查看该用户 |
| `source` | string | 来源：`APP`（App 内提交）/ `MAIL`（邮件渠道汇总，预留） |
| `contactEmail` | string | 用户留下的联系邮箱，可能为空串 |
| `content` | string | 反馈内容 |
| `attachments` | array | 附件列表，无附件为空数组 `[]`，见下表 |
| `status` | string | 处理状态 code，见 4.2 |
| `statusLabel` | string | 状态中文文案（如「处理中」），可直接展示 |
| `adminNote` | string | 处理备注，可能为空串；**会回显给 APP 端用户**，注意措辞 |
| `createdAt` / `updatedAt` | string | ISO 本地时间 |

**附件 `AppFeedbackAttachmentVO`**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `url` | string | **预签名下载 URL，有效期 1 小时**；OSS 未配置或签名失败时为空串，此时仅展示文件名等元信息 |
| `fileName` | string | 原始文件名 |
| `mimeType` | string | 如 `image/png`、`video/mp4` |
| `size` | number | 字节数 |
| `mediaType` | string | `IMAGE` / `VIDEO`，决定用 `<img>` 还是 `<video>` 预览 |

> **重要**：`url` 是 1 小时有效的 OSS 预签名链接（Bucket 私有读，无公开直链）。**及时展示、不要缓存复用**；页面停留过久链接过期后，重新拉取详情/列表即可换新。

### 4.2 处理状态枚举

`status` 只接受以下 code（大小写敏感）：

| code | label | 建议场景 |
|------|-------|----------|
| `PENDING` | 待处理 | 新提交的初始状态 |
| `PROCESSING` | 处理中 | 已认领 / 排查中 |
| `RESOLVED` | 已解决 | 处理完成 |

状态无强制流转顺序，允许任意切换（如误判可直接从 `RESOLVED` 改回 `PROCESSING`）。

---

## 5. 页面建议

- **列表页**：状态筛选下拉（待处理/处理中/已解决/全部）+ 关键字搜索框（提示「搜索内容/邮箱」）；表格列建议：提交时间、用户 ID、内容（超长截断 + 悬浮全文）、附件数、状态 tag（待处理-灰 / 处理中-蓝 / 已解决-绿）、操作（查看 / 更新状态）。
- **详情抽屉/页**：完整内容 + 联系邮箱（可一键复制）+ 附件预览（`mediaType=IMAGE` 缩略图点击放大，`VIDEO` 内联播放器）+ 状态流转表单（状态下拉 + 备注输入框，≤512 字计数器）。
- **更新状态交互**：从 `PENDING` 首次处理时建议默认带出 `PROCESSING`；标记 `RESOLVED` 时建议引导填写备注（非强制），因为备注会回显给用户。
- 待处理数量可作为列表页角标（用 `status=PENDING` 拉一次取 `total`）。

---

## 6. 常见业务错误

| 场景 | code | msg | 前端建议 |
|---|---|---|---|
| Token 失效/未登录 | 30007 | 「未登录或登录已失效，请重新登录」 | 全局拦截跳登录页 |
| 参数校验失败 | 30001 | 如「处理状态不能为空」「处理备注不能超过 512 字」 | 表单内联提示 |
| status 非法 | 30100 | 「反馈状态不合法」 | 提交前用固定枚举，正常不应触发 |
| 反馈不存在 | 30100 | 「反馈不存在」 | toast 后刷新列表 |

---

## 7. 联调注意事项

1. **数据库前置**：需执行 `database/mysql/ida_v12_app_feedback.sql`（在 `ida_v11_job_appliaction.sql` 之后）。
2. **附件预览依赖 OSS**：管理端进程需配置 `ida.aliyun.oss.enabled=true` 及 endpoint/bucket/AK/SK（与 C 端一致），否则附件 `url` 返回空串——不影响反馈数据本身的查看。
3. **预签名 URL 时效 1 小时**：附件链接过期后重新拉取详情/列表换新，不要持久化 url。
4. **无删除接口**：当前版本后台不提供删除反馈的能力。
5. **测试数据**：可直接用 C 端 App 调 `POST /api/v1/app/feedback` 提交几条（见《用户反馈模块 · 前端对接说明》），再回后台验证列表/详情/状态流转。
