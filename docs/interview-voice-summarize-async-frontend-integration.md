# 面试复盘 · 语音总结异步接口 · 前端对接说明

面向 **正式客户端**，进程 `ida-app-server`，Base：`/api/v1/app/interview`。

本文档描述**新版异步**语音/文本 AI 总结接口（提交 + 轮询）。
旧同步接口 `POST /sessions/{sessionId}/voice-summarize` 仍保留可用，二者返回的总结结果结构完全一致；
建议切换到异步版——录音场景下同步接口会挂起「音频时长 + 大模型耗时」这么久的 HTTP 连接（分钟级），极易触发网关/浏览器超时。

---

## 1. 流程总览

```
① POST /sessions/{sessionId}/voice-summarize/tasks（multipart 或 JSON）
        ↓ 立即返回（毫秒级），data = taskId
② GET  /sessions/{sessionId}/voice-summarize/tasks/{taskId}   ← 每 2~3 秒轮询
        ↓ status = RUNNING：继续轮询
        ↓ status = SUCCESS：data.result 即总结结果（与旧同步接口 data 结构相同）
        ↓ status = FAILED：展示 data.errorMessage，允许用户重试
```

**结果不入库**：与同步版一致，`result` 只是结构化草稿；用户确认后仍需调 `POST /sessions/{sessionId}/questions` 保存。

---

## 2. 提交任务（两种 Content-Type，与同步版一一对应）

### 2.1 multipart 版（上传录音，可附带浏览器转写文本）

`POST /api/v1/app/interview/sessions/{sessionId}/voice-summarize/tasks`
`Content-Type: multipart/form-data`

| 字段 | 必填 | 说明 |
|---|---|---|
| `file` | 否 | 录音文件。格式 **wav / pcm / opus（webm/ogg）**；**不支持 mp3**；≤ 30MB（HTTP 硬上限） |
| `rawTranscript` | 否 | 浏览器端已转写的文本；长度达到服务端阈值时**跳过服务端 ASR**，直接进大模型总结（更快、更省） |

`file` 与 `rawTranscript` 至少给一个，否则任务 FAILED。

### 2.2 JSON 版（纯文本口述/已有转写稿）

`POST /api/v1/app/interview/sessions/{sessionId}/voice-summarize/tasks`
`Content-Type: application/json`

```json
{ "rawTranscript": "面试官问：…… 我回答：……" }
```

### 响应（两种形态相同）

```json
{ "code": 0, "ok": true, "data": "66dd0cf0-2ce1-4868-9cc5-23efaa59f530" }
```

`data` 为 **taskId**，保存用于轮询。

---

## 3. 轮询任务状态

`GET /api/v1/app/interview/sessions/{sessionId}/voice-summarize/tasks/{taskId}`

### 响应 `data` 结构

```json
{
  "code": 0,
  "ok": true,
  "data": {
    "taskId": "66dd0cf0-...",
    "status": "SUCCESS",
    "createdAt": 1761287728848,
    "updatedAt": 1761287769275,
    "result": { "questions": [ { "question": "...", "myAnswer": "...", "answerEvaluation": "...", "referenceAnswer": "..." } ] },
    "errorMessage": null
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | `RUNNING` / `SUCCESS` / `FAILED` |
| `result` | object \| null | **仅 SUCCESS 有值**，结构与旧同步接口 `voice-summarize` 的 `data` 完全相同（`hint` / `extractedSession` / `questions` 等），现有渲染逻辑直接复用 |
| `errorMessage` | string \| null | **仅 FAILED 有值**，用户友好文案，直接展示 |
| `createdAt` / `updatedAt` | number | Epoch 毫秒 |

### 轮询策略建议

- 间隔 **2~3 秒**；
- **超时要按输入形态区分**：
  - 纯文本（跳过 ASR）：约 10~60 秒，建议超时 **90 秒**；
  - 录音文件：服务端听写按音频时长回放 + 大模型总结，3 分钟录音 ≈ 3~4 分钟，建议超时 **音频时长 + 90 秒**；
- 任务结果保留 **30 分钟**；超时后轮询返回业务错误「任务不存在、已过期或无权访问」（code `30100`），引导用户重新提交；
- 页面切换/刷新后可凭 taskId + sessionId 继续轮询。

### 注意：URL 里的 sessionId 必须与任务所属场次一致

拿别的场次的 URL 查任务会返回「任务不存在、已过期或无权访问」。前端拼装轮询 URL 时不要串场次。

---

## 4. 失败文案（FAILED 时 errorMessage 可能出现）

| errorMessage | 含义 / 建议动作 |
|---|---|
| 无法识别语音，请重录或改用文字 | 录音无有效人声或格式不支持；引导重录或改文字输入 |
| 听写服务未启用，请改用文字输入或配置 ida.speech-to-text | 服务端未配置 ASR；引导文字输入 |
| 复盘不存在或无权访问 | sessionId 无效或不属于当前用户 |
| AI 额度不足，请充值或明日再来 | 引导充值/兑换码 |
| 系统异常，请稍后重试 | 兜底错误，提供重试按钮 |

文案均为服务端最终用户文案，**直接展示，不要包装**。

---

## 5. 与旧同步接口的关系

| | 旧：`POST .../voice-summarize` | 新：`POST .../voice-summarize/tasks` + 轮询 |
|---|---|---|
| 连接占用 | 全程挂起（录音场景分钟级） | 提交毫秒级返回 |
| 结果结构 | `data` = 总结结果 | `data.result` = 同样的总结结果 |
| 入库行为 | 均不入库，确认后调 questions 接口 | 同左 |

旧接口保留作兼容/回退；新接入请直接用异步版。

---

## 6. 本地联调

- 服务：`ida-app-server`，默认 `http://localhost:8088`
- Knife4j：`http://localhost:8088/doc.html`，分组「正式客户端」，搜 `voice-summarize`
- 测试账号见 `docs/test-resume.md`（mock 验证码登录）
- 纯文本路径（JSON 形态 + 足够长的 rawTranscript）无需真实录音即可联调全链路；录音路径需要真实人声音频（纯静音/噪声会被 ASR 拒识）
