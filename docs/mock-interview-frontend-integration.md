# 模拟面试模块前端对接文档

## 1. 模块概述

模拟面试模块根据用户选择的简历，调用大模型生成一套面试题，并支持：

- 查看历史模拟面试列表
- 查看单场模拟面试详情及题目
- 创建新的模拟面试
- 删除模拟面试
- 更新模拟面试状态
- 保存练习进度（当前做到哪题、是否展开答案）

> 所有接口均需要 **Bearer Token** 鉴权。

---

## 2. 基础信息

| 项 | 值 |
|---|---|
| 基础路径 | `http://localhost:8088/api/v1/app` |
| 接口模块 | `/mock-interview` |
| 鉴权方式 | `Authorization: Bearer <token>` |
| Content-Type | `application/json` |
| 响应包装 | 统一返回 `ResponseDTO<T>`，业务数据在 `data` 字段 |

### 2.1 通用响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "success": true
}
```

- `code == 200` 且 `success == true` 表示业务成功。
- 业务异常（如简历不存在）会返回 `code != 200`，`message` 中携带中文提示。

---

## 3. 状态定义

模拟面试只有以下三种状态，前端交互时请不要传入其他值。

| 状态 | 含义 | 建议前端展示 |
|---|---|---|
| `GENERATED` | 已生成题目，尚未开始练习 | "待开始" / "未开始" |
| `PRACTICING` | 练习中 | "练习中" |
| `COMPLETED` | 已完成全部练习 | "已完成" |

### 状态流转建议

```
创建成功 → GENERATED
用户点击"开始练习" → PRACTICING
用户完成最后一题 / 点击"结束面试" → COMPLETED
```

> 状态更新由前端根据用户行为调用 `PUT /{sessionId}/status` 完成。

---

## 4. 接口清单

### 4.1 获取模拟面试列表

```http
GET /api/v1/app/mock-interview
Authorization: Bearer <token>
```

**响应 `data`**：

```json
[
  {
    "mockInterviewId": 1001,
    "resumeId": 2001,
    "title": "Java 后端开发 · 模拟面试",
    "questionCount": 8,
    "status": "GENERATED",
    "updatedAt": "2026-07-12T18:30:00"
  }
]
```

---

### 4.2 获取模拟面试详情

```http
GET /api/v1/app/mock-interview/{sessionId}
Authorization: Bearer <token>
```

**响应 `data`**：

```json
{
  "mockInterviewId": 1001,
  "resumeId": 2001,
  "title": "Java 后端开发 · 模拟面试",
  "questionCount": 8,
  "status": "PRACTICING",
  "updatedAt": "2026-07-12T18:35:00",
  "mockInterviewProgress": {
    "currentQuestionId": 5001,
    "answerRevealed": 0
  },
  "mockInterviewQuestions": [
    {
      "id": 5001,
      "sessionId": 1001,
      "sortOrder": 1,
      "question": "请介绍一下你在简历中提到的秒杀系统项目，你是如何应对高并发的？",
      "referenceAnswer": "可以从限流、缓存、异步削峰、库存扣减方案等角度回答...",
      "answerEvaluation": "考察高并发设计、Redis 使用、消息队列选型",
      "otherNotes": "可追问分布式锁实现",
      "createdAt": "2026-07-12T18:30:00",
      "updatedAt": "2026-07-12T18:30:00"
    }
  ]
}
```

> `answerRevealed`：0 表示当前题答案未展开，1 表示已展开。

---

### 4.3 创建模拟面试

```http
POST /api/v1/app/mock-interview
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：

```json
{
  "resumeId": 2001,
  "title": "Java 后端开发 · 模拟面试"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `resumeId` | 是 | 简历 ID，必须是当前登录用户的简历 |
| `title` | 否 | 面试标题；不传时后端自动生成，如 `"Java 后端开发 · 模拟面试"` |

**响应 `data`**：新创建的模拟面试 ID（`Long`）

```json
{
  "code": 200,
  "message": "success",
  "data": 1001,
  "success": true
}
```

> 创建过程会调用大模型生成题目，通常需要 3–10 秒，前端建议展示"题目生成中"的友好 loading 状态，并设置超时/重试策略。

---

### 4.4 删除模拟面试

```http
DELETE /api/v1/app/mock-interview/{sessionId}
Authorization: Bearer <token>
```

**响应 `data`**：`null`

---

### 4.5 更新模拟面试状态

```http
PUT /api/v1/app/mock-interview/{sessionId}/status?status=PRACTICING
Authorization: Bearer <token>
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `status` | 是 | 仅允许 `GENERATED` / `PRACTICING` / `COMPLETED` |

**响应 `data`**：`null`

---

### 4.6 保存练习进度

```http
PUT /api/v1/app/mock-interview/{sessionId}/progress
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：

```json
{
  "currentQuestionId": 5001,
  "answerRevealed": true
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `currentQuestionId` | 否 | 当前做到哪一题；传 `null` 表示不更新 |
| `answerRevealed` | 否 | 当前题目答案是否已展开：`true` / `false` |

> `currentQuestionId` 必须是本场模拟面试下的题目 ID，否则后端会报错。

**响应 `data`**：`null`

---

## 5. 前端对接流程建议

### 5.1 列表页

1. 进入页面调用 `GET /mock-interview`。
2. 列表项展示：标题、题目数量、状态、更新时间。
3. 操作按钮：
   - 状态 `GENERATED`：显示"开始练习"，点击后先 `PUT /status?status=PRACTICING`，再跳转详情页。
   - 状态 `PRACTICING`：显示"继续练习"，直接跳转详情页。
   - 状态 `COMPLETED`：显示"查看复盘"，跳转详情页。
   - 每个卡片/行提供"删除"入口，确认后调用 `DELETE /{sessionId}`。

### 5.2 创建页

1. 用户选择一份简历。
2. 可选填写自定义标题，留空由后端生成。
3. 点击"生成模拟面试"，调用 `POST /mock-interview`。
4. 展示 loading，成功后跳转详情页或列表页。

### 5.3 练习详情页

1. 进入页面调用 `GET /mock-interview/{sessionId}`。
2. 拿到 `mockInterviewQuestions`，按 `sortOrder` 排序展示题目列表/卡片。
3. 根据 `mockInterviewProgress.currentQuestionId` 定位到当前题。
4. 根据 `answerRevealed` 控制参考答案是否展开：
   - 未展开：显示"查看答案"按钮。
   - 点击后本地将 `answerRevealed` 置为 `true`，同时调用 `PUT /progress` 保存。
5. 用户切换下一题时，调用 `PUT /progress` 更新 `currentQuestionId`。
6. 做到最后一题且用户点击"完成"时，调用 `PUT /status?status=COMPLETED`。

---

## 6. 常见业务错误

| 场景 | 后端返回 message | 前端建议处理 |
|---|---|---|
| 简历 ID 为空 | "resumeId不能为空" | 创建前校验必填 |
| 简历不属于当前用户 | "简历不存在或无权访问" | 提示用户选择自己的简历 |
| 模拟面试不存在 | "模拟面试不存在或无权访问" | 返回列表页或 404 提示 |
| 状态值非法 | "无效的模拟面试状态" | 下拉框限制可选值 |
| 题目不属于本场面试 | "当前题目不存在或不属于本场模拟面试" | 检查 currentQuestionId 是否正确 |

---

## 7. UI/UX 建议

- **创建 loading**：大模型生成耗时较长，建议显示"AI 正在根据简历生成面试题..."，并允许取消。
- **题目卡片**：默认只展示题目，隐藏参考答案和评分维度，用户主动点击后再展开，模拟真实面试。
- **进度持久化**：每次切题、展开答案都调用保存接口，避免刷新页面后丢失进度。
- **空状态**：列表为空时引导用户去简历页创建简历或生成模拟面试。
- **状态标签**：用不同颜色区分 `GENERATED`（灰色）、`PRACTICING`（蓝色）、`COMPLETED`（绿色）。

---

## 8. 联调注意事项

1. **鉴权头格式**：必须是 `Authorization: Bearer <token>`，token 通过登录接口获取。
2. **Swagger 调试**：优先使用 `http://localhost:8088/swagger-ui/index.html`；Knife4j 4.5.0 存在 Authorization 头不生效的已知问题，后端已通过 `GlobalOpenApiCustomizer` 做了兼容，但仍建议用 swagger-ui 调试。
3. **超时设置**：创建接口建议设置 30 秒以上超时。
4. **题目顺序**：展示时按 `sortOrder` 升序，不要依赖数据库返回顺序。

---

## 9. 附录：完整时序示例

```text
1. 用户选择简历 → 点击"生成模拟面试"
   POST /api/v1/app/mock-interview
   { "resumeId": 2001 }
   ← data: 1001

2. 跳转详情页
   GET /api/v1/app/mock-interview/1001
   ← 返回 8 道题目，状态 GENERATED

3. 用户点击"开始练习"
   PUT /api/v1/app/mock-interview/1001/status?status=PRACTICING

4. 用户查看第一题答案
   PUT /api/v1/app/mock-interview/1001/progress
   { "currentQuestionId": 5001, "answerRevealed": true }

5. 用户切换/做到第二题
   PUT /api/v1/app/mock-interview/1001/progress
   { "currentQuestionId": 5002, "answerRevealed": false }

6. 用户完成全部题目
   PUT /api/v1/app/mock-interview/1001/status?status=COMPLETED
```
