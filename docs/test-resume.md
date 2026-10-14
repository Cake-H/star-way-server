# 测试简历与测试账号

`test-resume.docx`：用于简历上传解析（同步 `/resume/upload-parse` 与异步 `/resume/upload-parse/tasks`）联调的测试简历，
内容为虚构人物「张三」（Java 后端，5 年经验），涵盖教育/工作/项目/技能/证书/获奖各模块，可验证 LLM 结构化解析的完整性。

## 测试账号（仅 dev 环境，mock 验证码）

dev 配置开启了模拟登录（`ida.sms.code.mock-code-enabled=true`，生产必须关闭）：

| 手机号 | 验证码 | 说明 |
|---|---|---|
| 13800000000 | 246810 | 模拟号 1 |
| 13800000001 | 246810 | 模拟号 2 |

登录：`POST /api/v1/app/auth/sms/login`，body `{"phone":"13800000000","code":"246810"}`，返回 `data.token` 作为 `Authorization: Bearer <token>`。

> 注意：AI 调用受计费额度约束。新账号首次调用 AI 链路时会触发当日赠送额度；
> 若报「AI 额度不足」说明当日额度已用完，可换另一个模拟号或次日再测。

## 异步解析接口冒烟

```bash
# 1. 提交任务（立即返回 taskId）
curl -X POST http://127.0.0.1:8088/api/v1/app/resume/upload-parse/tasks \
  -H "Authorization: Bearer <token>" \
  -F "file=@docs/test-resume.docx;type=application/vnd.openxmlformats-officedocument.wordprocessingml.document"

# 2. 轮询结果（RUNNING → SUCCESS/FAILED）
curl http://127.0.0.1:8088/api/v1/app/resume/upload-parse/tasks/{taskId} \
  -H "Authorization: Bearer <token>"
```

## 已验证的测试结论（2026-09，阶段 B 完成后）

- 正常路径：docx → OSS 上传 + 文本抽取并行 → LLM 结构化 → SUCCESS，`draft` 各模块条数正确（教育 1、工作 2、项目 1、技能 4、证书 1、获奖 1）；
- 失败路径：>15MB 文件 → FAILED，`errorMessage` 为用户友好文案「文件过大，请上传不超过 15MB 的简历」；
- 额度路径：余额不足 → FAILED，`errorMessage` 为「AI 额度不足，请充值或明日再来」；
- 越权路径：用他人 token 查 taskId → 「任务不存在、已过期或无权访问」，不泄露任务是否存在；
- HTTP 层限制：上传文件 >30MB 会在 Spring multipart 层被拒（`max-file-size: 30MB`），到不了业务校验。
