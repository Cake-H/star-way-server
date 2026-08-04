# 隐私安全基线（任务 1.4）— 实施说明与验收手册

> 对应任务清单 `docs/xingtu-sprint-tasklist.md` 1.4 节。
> 目标：用户简历/个人信息**不明文落库、不明文进 LLM prompt、不公开可访问、可注销可删除**。

---

## 一、实施内容总览

### 1. AI 请求脱敏（4 条 LLM 链路，占位符映射 + 还原）

公共组件：`ida-common` → `com.smartapply.ida.security.mask.SensitiveDataMasker`

- `mask(text)`：手机号 / 身份证号 / 邮箱 → `[PHONE_1]`、`[IDCARD_1]`、`[EMAIL_1]` 占位符，映射仅存本次请求内存；
- `restore(text, session)`：LLM 返回后把占位符还原为真值（功能无损）；
- `maskMiddle` / `maskSensitive`：不可逆打码，用于日志与反馈明细。

| 链路 | 位置 | 做法 |
|---|---|---|
| 简历解析 | `ida-app` `ResumeArkStructuringService` | 简历纯文本 mask → prompt；返回 DTO JSON 往返 restore |
| 插件填表 | `ida-plugin` `PluginFormAutofillAiService` | 简历 draft JSON（PART_A）mask → prompt；plan 的 value/fallback/subAssignments restore |
| 面试总结 | `ida-app` `InterviewSummarizeAiService` | 转写全文 mask；返回 VO JSON 往返 restore |
| 模拟面试 | `ida-app` `MockInterviewGenerateAiService` | 简历上下文 mask；返回 VO JSON 往返 restore |

`PluginAutofillFeedbackConsumer.maskAndTruncate` 已重构为复用公共组件（并新增邮箱脱敏）。

### 2. 存储加密

- **简历主表**（`ida_resume`）：`fullName / idDocNumber / phone / email / qq / wechat / detailAddress / mailingAddress / emergencyContactName / emergencyContactPhone` 通过 `AesStringTypeHandler`（随机 IV AES-256）透明加解密，业务代码零改动；解密失败回退原值，兼容存量明文。
- **登录手机号**（`ida_user.phone`）：`PhoneCrypto` 改用**确定性 AES**（固定 IV，`Encryptors.text`），修复随机 IV 导致登录等值查询永远落空的问题。
- **盲索引**：`ida_user.phone_tail`（后 4 位明文）——管理后台支持"完整手机号（密文等值）/ 后 4 位（盲索引模糊）/ 用户 ID"三种搜索，是业界兼顾加密与检索的标准做法。
- 管理端展示：先解密再前 3 后 4 打码。
- 配置：`ida.crypto.aes-field.enabled=true`（app/plugin/admin 三个 server），密钥走环境变量 `IDA_AES_FIELD_SECRET` / `IDA_AES_FIELD_SALT_HEX`（16 位 hex）。
- DDL：`database/mysql/ida_v8_privacy_security.sql`（列扩长 + `ida_user.deleted_at` + `phone_tail`）。

### 3. 传输与权限

- **OSS**：`AliyunOssStorageService.generatePresignedUrl()`；上传解析接口返回 `ossSignedUrl`（1 小时过期）替代公开直链（VO 字段 `ossPublicUrl` → `ossSignedUrl`）；**Bucket 必须设为私有读**。
- **HTTPS**：`deploy/nginx/nginx.conf`（443 SSL 终止 + 安全响应头 + 80 跳转 + 抹除伪造 `X-User-Id`），compose 新增 nginx 服务；证书放 `deploy/nginx/certs/`。
- **CORS**：`ida.cors.allowed-origin-patterns` 配置化（yml 已加生产收敛警告注释）。
- **调试头**：`X-User-Id` 新增开关 `ida.http.identity.user-id-header-enabled`，**默认 false**（生产安全），仅 dev profile 显式开启。
- **日志**：短信发送/登录/验证码等日志中的手机号全部 `maskMiddle` 打码。

### 4. 合规功能

- **协议**：`docs/privacy-policy.md`、`docs/user-agreement.md`（模板，上线前法务审核）；匿名接口 `GET /api/v1/app/agreement/{privacy|terms}` 返回 markdown。前端注册页需：展示两个协议链接 + 勾选"我已阅读并同意"后才允许注册（前端仓库实现）。
- **账号注销**：`DELETE /api/v1/app/user/account`，body `{"confirmText":"注销"}`。执行：手机号不可逆匿名化（`del_{userId}_{随机}`）→ 用户逻辑删除 → 简历 9 子表物理删 + 主表逻辑删 → 面试/模拟面试逻辑删 → 撤销当前 token。

---

## 二、部署步骤（上线 checklist）

1. 执行 DDL：`mysql ida_db < database/mysql/ida_v8_privacy_security.sql`
2. 生成密钥并写入 `.env`：
   ```bash
   openssl rand -hex 16   # → IDA_AES_FIELD_SECRET
   openssl rand -hex 8    # → IDA_AES_FIELD_SALT_HEX（必须恰好 16 位 hex）
   ```
3. 阿里云 OSS 控制台：Bucket 权限改为**私有读**；RAM 用户保留 `oss:PutObject` + `oss:GetObject`。
4. 放置 TLS 证书到 `deploy/nginx/certs/`，把 `deploy/nginx/nginx.conf` 中 `your-domain.com` 改为真实域名。
5. 生产 yml 将 `ida.cors.allowed-origin-patterns` 收敛为具体域名/扩展 ID；确认 `user-id-header-enabled` 未开启（默认 false）。
6. `docker compose up -d`。

⚠️ **存量数据**：`ida_user.phone` 改为确定性加密后，存量明文手机号无法登录匹配；简历表旧明文行可读（回退兼容）但建议开发期清库重建。

---

## 三、验收步骤

### 1. LLM 请求无明文 PII（抓包/日志）
- 触发简历解析/插件填表/面试总结/模拟面试各一次，在网关或代理（如 mitmproxy）抓取发往 `api.deepseek.com` 的请求体；
- **预期**：请求体中搜不到 11 位手机号、18 位身份证号、邮箱明文，只能看到 `[PHONE_1]`、`[IDCARD_1]`、`[EMAIL_1]` 占位符；
- 功能校验：解析出的简历草稿、填表值中手机号/邮箱为**真实值**（占位符已在服务端还原）。

### 2. 存储加密
- `SELECT phone, email, id_doc_number, full_name FROM ida_resume LIMIT 5;` → 应为 Base64 密文，无可读明文；
- `SELECT phone, phone_tail FROM ida_user LIMIT 5;` → phone 为 hex 密文，phone_tail 为 4 位数字；
- 应用读写正常：保存草稿 → 详情回显明文；短信登录正常（等值查询命中）。

### 3. OSS 不可匿名访问
- 匿名 `curl -I https://<bucket>.oss-cn-<region>.aliyuncs.com/app/resume/<uid>/<file>` → **403**；
- 上传解析接口返回的 `ossSignedUrl` 可下载；1 小时后同一 URL → 403/Expired。

### 4. HTTPS
- `curl -I http://<domain>/api/v1/app/...` → 301 到 https；
- https 响应头含 `Strict-Transport-Security`；
- 携带伪造 `X-User-Id: 1` 调用任意接口 → 不会变成用户 1（未带 Bearer 时 401）。

### 5. 账号注销
- 调用 `DELETE /api/v1/app/user/account`（confirmText=注销）；
- 预期：`ida_user` 该行 `phone='del_...'`、`phone_tail=NULL`、`deleted_at` 非空；简历/面试记录 `deleted_at` 非空；子表无残留行；原 token 再请求 → 401；原手机号可重新注册为新账号。

### 6. 日志
- 触发发送验证码/登录，`grep phone` 服务日志 → 只出现 `138****8000` 形式。

---

## 四、明确不做（风险披露）

- 简历子表的公司名/学校名**未加密**（弱敏感 + 检索需求，已评估接受）；
- 语音**音频**原文会发给 ASR 服务商（阿里云 NLS / OpenAI Whisper），已在隐私政策披露；
- ASR 之外，插件填表发给浏览器扩展的字段值为明文（功能必需，端侧使用）；
- 插件端登录 token 与 app 端相互独立，注销时仅撤销 app 端当前 token，插件 token 随 7 天 TTL 自然过期。
