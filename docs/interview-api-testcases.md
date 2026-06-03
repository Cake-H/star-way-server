# 面试复盘模块 · Apifox 测试说明

Base：`http://localhost:8088/api/v1/app/interview`  
鉴权：`Authorization: Bearer {token}` 或调试头 `X-User-Id: {userId}`

## voice-summarize 响应结构（多题 + 场次元信息）

```json
{
  "code": 0,
  "ok": true,
  "success": true,
  "msg": "操作成功",
  "message": "操作成功",
  "data": {
    "hint": null,
    "extractedSession": {
      "companyName": "字节跳动",
      "positionName": "后端开发",
      "interviewRound": "一面",
      "interviewAt": "2026-06-02",
      "suggestedTitle": "字节跳动 · 后端开发 · 一面"
    },
    "questions": [
      {
        "question": "请介绍你的核心项目",
        "myAnswer": "…",
        "answerEvaluation": "…",
        "referenceAnswer": "…",
        "otherNotes": "…"
      }
    ],
    "question": "请介绍你的核心项目",
    "myAnswer": "…",
    "answerEvaluation": "…",
    "referenceAnswer": "…",
    "otherNotes": "…"
  }
}
```

前端请以 **`data.questions`** 为主；顶层五字段为 `questions[0]` 的兼容副本。

## JSON 文本总结示例

`POST /sessions/{sessionId}/voice-summarize`

```json
{
  "rawTranscript": "今天面试了字节跳动的后端开发一面。面试官问了项目经历，我说做了订单模块…"
}
```

## 联调顺序

1. `POST /sessions` 创建场次  
2. `POST /sessions/{id}/voice-summarize`  
3. `PUT /sessions/{id}` 应用 `extractedSession`（可选）  
4. 对 `questions` 逐条 `POST /sessions/{id}/questions`  
5. `GET /sessions/{id}` 校验详情  
