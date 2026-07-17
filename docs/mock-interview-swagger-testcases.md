# 模拟面试接口 Swagger 测试指南

> 服务启动后，直接访问 Swagger UI 即可在线调试，无需额外导入。

## 访问地址

```
http://localhost:8088/swagger-ui/index.html
```

或

```
http://localhost:8088/swagger-ui
```

左侧 Tag 中找到 **「模拟面试」**，展开后可以看到全部接口。

## 前置准备

1. 启动 `ida-app-server` 和 Redis。
2. 确保数据库里有一份当前用户的简历，记下 `resumeId`。
3. 确保 `ARK_API_KEY_DEEPSEEK` 环境变量已配置（创建接口会调 LLM）。

## 登录获取 Token

模拟面试接口需要登录态。Swagger 右上角现在已有 **Authorize** 按钮，测试前请先获取 token：

### 1. 发送验证码

找到 **「正式客户端-认证」** → `POST /api/v1/app/auth/sms/send-code`

- 点击 **Try it out**
- Request body：

```json
{
  "phone": "13800138000"
}
```

> 把手机号换成你数据库里用户表存在的手机号，或任意能收到验证码的测试号。

- 点击 **Execute**
- **预期**：返回 `code=0`

### 2. 短信登录

找到 **「正式客户端-认证」** → `POST /api/v1/app/auth/sms/login`

- Request body：

```json
{
  "phone": "13800138000",
  "code": "123456"
}
```

> 若本地短信验证码走的是测试/mock 通道，通常固定码是 `123456`；具体看 `SmsVerificationService` 实现。

- 点击 **Execute**
- **预期**：返回 `code=0`，`data.token` 为登录 token
- 复制 token 值（不需要带 `Bearer ` 前缀）

### 3. 在 Swagger 中设置全局 Token

- 点击页面右上角 **Authorize** 按钮
- 在弹出的 Bearer Auth 输入框中填入：`Bearer <你的token>`
- 点击 **Authorize** → **Close**

之后所有带锁图标的接口都会自动在请求头中携带 `Authorization: Bearer <token>`。

> 登录、发验证码、健康检查等 `@NoNeedLogin` 接口不带锁，不需要设置 token 也能直接调用。

## 接口调试顺序

### 1. POST /api/v1/app/mock-interview 创建模拟面试

- 点击 **Try it out**
- Request body：

```json
{
  "resumeId": 1,
  "title": "Java 后端开发 · 模拟面试"
}
```

- 点击 **Execute**
- **预期**：返回 `code=0`，`data` 为新建的 `sessionId`，例如 `10`
- 把返回的 `sessionId` 记下来

---

### 2. GET /api/v1/app/mock-interview 获取列表

- 点击 **Execute**
- **预期**：返回当前用户的模拟面试列表，包含刚创建的 session

---

### 3. GET /api/v1/app/mock-interview/{sessionId} 获取详情

- `sessionId` 填上一步返回的 ID
- 点击 **Execute**
- **预期**：返回 session 元信息、`mockInterviewProgress` 和 `mockInterviewQuestions` 题目列表
- 记下第一个题目的 `id`，后面保存进度会用到

---

### 4. PUT /api/v1/app/mock-interview/{sessionId}/status 更新状态

- `sessionId` 填上一步的 ID
- `status` 填 `PRACTICING`
- 点击 **Execute**
- **预期**：返回 `code=0`
- 再查一次详情，确认 `status` 已变为 `PRACTICING`

---

### 5. PUT /api/v1/app/mock-interview/{sessionId}/progress 保存练习进度

- `sessionId` 填上一步的 ID
- Request body：

```json
{
  "currentQuestionId": 1,
  "answerRevealed": true
}
```

> 把 `currentQuestionId` 换成你从详情里拿到的真实题目 ID。

- 点击 **Execute**
- **预期**：返回 `code=0`
- 再查一次详情，确认 `mockInterviewProgress` 已更新

---

### 6. DELETE /api/v1/app/mock-interview/{sessionId} 删除模拟面试

- `sessionId` 填上面的 ID
- 点击 **Execute**
- **预期**：返回 `code=0`

---

### 7. GET /api/v1/app/mock-interview/{sessionId} 删除后查详情

- 用同一个 `sessionId` 再查一次
- **预期**：返回失败信息，提示「模拟面试不存在或无权访问」

---

## 异常场景测试

### A. 创建时 resumeId 为空

```json
{
  "title": "测试"
}
```

**预期**：提示参数错误。

### B. 创建时 resumeId 不属于当前用户

```json
{
  "resumeId": 99999,
  "title": "越权测试"
}
```

**预期**：提示「简历不存在或无权访问」。

### C. 更新状态为非法值

`status` 填 `INVALID`。

**预期**：提示「无效的模拟面试状态」。

### D. 保存进度时 currentQuestionId 不属于当前 session

```json
{
  "currentQuestionId": 99999,
  "answerRevealed": false
}
```

**预期**：提示「当前题目不存在或不属于本场模拟面试」。

### E. 越权访问其他用户的 session

用另一个账号登录获取 token，然后访问当前 token 用户没有权限的 `sessionId`。

**预期**：提示「模拟面试不存在或无权访问」。

---

## 小贴士

- Swagger 右上角 **Authorize** 设置的是全局 Bearer Token，设置一次即可测试所有带锁接口。
- 如果本地不想走登录流程（比如 Redis 没起），仍然可以直接在请求头里传 `X-User-Id`：
  - 点击接口的 **Try it out**
  - 找到 `X-User-Id` 参数框，填入用户 ID
  - 注意 Bearer Token 和 `X-User-Id` 同时存在时，以 Bearer Token 解析出的用户为准
- 创建接口调用 LLM，耗时较长，如果超时失败，可以检查 `ida.langchain4j.mock-interview.read-timeout-ms` 配置。
