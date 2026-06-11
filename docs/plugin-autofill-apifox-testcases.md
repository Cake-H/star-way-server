# 插件一键填表接口 Apifox 测试用例

目标接口：
- `POST /api/v1/plugin/resume/autofill-plan`
- `POST /api/v1/plugin/resume/autofill-plan/tasks`
- （配套轮询）`GET /api/v1/plugin/resume/autofill-plan/tasks/{taskId}`

---

## 0. 测试前准备

- 基础地址示例：`http://localhost:8080`
- 必填请求头：
  - `Content-Type: application/json`
  - `X-User-Id: 你的用户ID`（例如 `10001`）
- 先确保你传入的 `resumeId` 是该用户真实存在且有权限访问的简历 ID。

统一响应包结构（后端约定）：

```json
{
  "code": 0,
  "level": null,
  "ok": true,
  "msg": "操作成功",
  "data": {}
}
```

---

## 1) 同步接口：`POST /autofill-plan`

说明：接口会阻塞直到模型返回，`data` 直接是 `PluginAutofillPlanResultVO`。

### 用例 1：标准成功（fillable=true，含 assignments）

**请求体（可直接贴 Apifox）**

```json
{
  "resumeId": 1,
  "page": {
    "pageUrl": "https://jobs.example.com/apply/123",
    "pageTitle": "校园招聘-简历投递",
    "fields": [
      {
        "selector": "#name",
        "tagName": "input",
        "inputType": "text",
        "labelText": "姓名",
        "placeholder": "请输入姓名",
        "visible": true,
        "clientFieldKey": "resume.fullName"
      },
      {
        "selector": "#phone",
        "tagName": "input",
        "inputType": "tel",
        "labelText": "手机号",
        "placeholder": "请输入手机号",
        "visible": true,
        "clientFieldKey": "resume.phone"
      },
      {
        "selector": "#project_desc",
        "tagName": "textarea",
        "labelText": "项目描述",
        "visible": true,
        "clientFieldKey": "resume.project.description"
      }
    ]
  }
}
```

**预期响应（示例）**

```json
{
  "code": 0,
  "ok": true,
  "msg": "操作成功",
  "data": {
    "fillable": true,
    "hint": "已匹配到主要简历字段",
    "assignments": [
      {
        "selector": "#name",
        "value": "张三",
        "order": 1,
        "confidence": 0.99,
        "matchType": "exact",
        "clientFieldKey": "resume.fullName",
        "subAssignments": []
      },
      {
        "selector": "#phone",
        "value": "13800138000",
        "order": 2,
        "confidence": 0.98,
        "matchType": "exact",
        "clientFieldKey": "resume.phone",
        "subAssignments": []
      }
    ],
    "blockedFields": []
  }
}
```

---

### 用例 2：页面可用控件为空（快速返回 fillable=false，不调用模型）

**请求体**

```json
{
  "resumeId": 1,
  "page": {
    "pageUrl": "https://jobs.example.com/home",
    "pageTitle": "首页",
    "fields": []
  }
}
```

**预期响应关键点**
- `code=0`、`ok=true`
- `data.fillable=false`
- `data.assignments=[]`
- `data.hint` 会提示“未采集到可编辑控件”

---

### 用例 3：有控件但模型未能可靠对齐（sanitize 后转不可填）

**请求体**

```json
{
  "resumeId": 1,
  "page": {
    "pageUrl": "https://jobs.example.com/apply/complex",
    "pageTitle": "复杂动态页",
    "fields": [
      {
        "selector": "#unknown_1",
        "tagName": "input",
        "inputType": "text",
        "labelText": "A1"
      },
      {
        "selector": "#unknown_2",
        "tagName": "input",
        "inputType": "text",
        "labelText": "A2"
      }
    ]
  }
}
```

**预期响应关键点**
- `code=0`、`ok=true`
- 可能出现 `data.fillable=false`
- `data.hint` 类似“未能与简历字段可靠对齐”
- `assignments` 为空，`blockedFields` 可能有值也可能为空（与模型返回有关）

---

### 用例 4：参数缺失（resumeId 缺失）

**请求体**

```json
{
  "page": {
    "pageUrl": "https://jobs.example.com/apply/123",
    "pageTitle": "投递页",
    "fields": [
      {
        "selector": "#name"
      }
    ]
  }
}
```

**预期响应关键点**
- `code=30001`
- `ok=false`
- `msg` 包含：`resumeId 不能为空`

---

### 用例 5：字段校验失败（selector 为空）

**请求体**

```json
{
  "resumeId": 1,
  "page": {
    "pageUrl": "https://jobs.example.com/apply/123",
    "pageTitle": "投递页",
    "fields": [
      {
        "selector": "",
        "tagName": "input",
        "inputType": "text"
      }
    ]
  }
}
```

**预期响应关键点**
- `code=30001`
- `ok=false`
- `msg` 包含：`selector 不能为空`

---

### 用例 6：未带用户头（鉴权失败）

操作方式：去掉 `X-User-Id` 再请求任一接口。

**预期响应关键点**
- HTTP 状态码 `401`
- 响应体 `code=30007`
- `msg=未登录或登录已失效，请重新登录`

---

## 2) 异步提交接口：`POST /autofill-plan/tasks`

说明：该接口只负责“创建任务”，不直接返回填表计划。

### 用例 7：异步提交成功（返回 taskId）

**请求体（可与用例1相同）**

```json
{
  "resumeId": 1,
  "page": {
    "pageUrl": "https://jobs.example.com/apply/123",
    "pageTitle": "校园招聘-简历投递",
    "fields": [
      {
        "selector": "#name",
        "tagName": "input",
        "inputType": "text",
        "labelText": "姓名"
      },
      {
        "selector": "#phone",
        "tagName": "input",
        "inputType": "tel",
        "labelText": "手机号"
      }
    ]
  }
}
```

**预期响应（示例）**

```json
{
  "code": 0,
  "ok": true,
  "msg": "操作成功",
  "data": {
    "taskId": "c6f388f2-6952-4fbc-b8a8-57af4ef7f5a3",
    "pollPathTemplate": "/api/v1/plugin/resume/autofill-plan/tasks/{taskId}"
  }
}
```

---

### 用例 8：异步任务轮询成功终态（SUCCEEDED）

拿到 `taskId` 后，调用：
- `GET /api/v1/plugin/resume/autofill-plan/tasks/{taskId}`

**预期响应关键点**
- `code=0`、`ok=true`
- `data.status` 可能经历：`PENDING -> RUNNING -> SUCCEEDED`
- `data.phase` 可能是：`RECEIVED/LOADING_RESUME/BUILDING_PROMPT/CALLING_MODEL/PARSING_MODEL_JSON/SANITIZING_PLAN/COMPLETED`
- 当 `status=SUCCEEDED` 时，`data.result` 非空，结构与同步接口 `data` 一致。

**SUCCEEDED 示例（节选）**

```json
{
  "code": 0,
  "ok": true,
  "data": {
    "taskId": "c6f388f2-6952-4fbc-b8a8-57af4ef7f5a3",
    "status": "SUCCEEDED",
    "phase": "COMPLETED",
    "message": "填表计划已生成",
    "percent": 100,
    "createdAtEpochMs": 1760000000000,
    "updatedAtEpochMs": 1760000009000,
    "result": {
      "fillable": true,
      "hint": "已匹配到主要简历字段",
      "assignments": [
        {
          "selector": "#name",
          "value": "张三",
          "order": 1
        }
      ],
      "blockedFields": []
    },
    "errorMessage": null
  }
}
```

---

### 用例 9：异步任务失败终态（FAILED）

触发方式示例：
- 用不存在/无权限的 `resumeId`
- 或后端模型配置异常（如 API Key 缺失）

**轮询预期关键点**
- `code=0`、`ok=true`（任务查询本身成功）
- `data.status="FAILED"`
- `data.phase="FAILED"`
- `data.errorMessage` 有可读错误信息
- `data.result=null`

**FAILED 示例（节选）**

```json
{
  "code": 0,
  "ok": true,
  "data": {
    "taskId": "c6f388f2-6952-4fbc-b8a8-57af4ef7f5a3",
    "status": "FAILED",
    "phase": "FAILED",
    "message": "任务失败",
    "percent": 100,
    "result": null,
    "errorMessage": "简历不存在或无权访问"
  }
}
```

---

### 用例 10：轮询不存在/过期任务

请求：
- `GET /api/v1/plugin/resume/autofill-plan/tasks/not-exists-task-id`

**预期响应关键点**
- `code=30100`
- `ok=false`
- `msg=任务不存在、已过期或无权访问`

---

## 3) Apifox 调试建议（实操）

- 同步接口适合先看结构：直接验证 `fillable/assignments/blockedFields`。
- 异步接口适合看完整过程：先 `POST /tasks` 拿 `taskId`，再每 `0.5~2s` 轮询一次。
- 你最关注 VO 的话，建议重点观察：
  - 同步：`data.assignments[*]` 的 `selector/value/order/confidence/matchType/subAssignments`
  - 异步：`data.status/phase/percent/result/errorMessage`
- 若要稳定复现“复合字段”场景（手机号区号+号码、起止日期），在 `fields` 里补充：
  - `compoundSegment`: `dial_code` / `local_number` / `date_start` / `date_end`
  - `interactionMode`: `single_select` / `combobox_typeahead` / `plain_text`

