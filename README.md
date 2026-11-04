# Maverick 智投 · 一站式求职效率平台（服务端）

面向求职场景的 AI 效率工具，提供**简历解析入库、浏览器插件一键填表、模拟面试、面试语音复盘**四大核心能力。
服务端拆分为 App 服务、浏览器插件服务、管理后台三个独立部署的进程，共享同一套领域模型与基础设施。

- 项目仓库：https://gitee.com/little-fox-helper/XHSocial-Maverick-server

## 核心功能

| 功能 | 说明 |
|---|---|
| 简历解析 | 上传 PDF/DOCX → OSS 存档 + 文本抽取 + 大模型结构化为可编辑草稿；支持异步任务（提交 + 轮询） |
| 一键填表 | 浏览器插件采集网申页面控件快照，服务端「站点模板 → 确定性匹配 → LLM 推理」三层降级流水线生成填值计划 |
| 模拟面试 | 按简历与目标岗位由大模型生成整套面试题，逐题作答与评估 |
| 面试复盘 | 录音经阿里云 NLS 转写后由大模型拆分为结构化问答复盘；支持异步任务 |
| 计费体系 | 额度守卫 → token 计量 → 按结果结算；兑换码核销；每日额度发放 |
| 管理后台 | 用户管理、用量看板、额度调整、兑换码管理、网申站点 CRUD、填表别名运营审核 |

## 架构

```
                        ┌─────────────┐
        浏览器/App ────►│    nginx    │ HTTPS 终止 + 反向代理 + 静态站点
                        └──────┬──────┘
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
 ┌────────────────┐  ┌─────────────────┐  ┌─────────────────┐
 │ ida-app-server │  │ida-plugin-server│  │ ida-admin-server│
 │   (App :8088)  │  │  (插件 :8089)   │  │  (后台 :8090)    │
 └───────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         ▼                    ▼                    ▼
 ┌────────────────────────────────────────────────────────┐
 │      MySQL（业务库） +  Redis（会话/验证码/缓存/任务队列）│
 └────────────────────────────────────────────────────────┘
         │
         ▼  
         外部服务：阿里云 OSS（原件存档）、阿里云 NLS（语音转写）、
           阿里云短信（验证码）、火山方舟大模型（DeepSeek 等，LangChain4j 接入）
```

三个进程为**独立 Spring Boot 应用**，共享 `ida-common`（领域模型/工具）与 `ida-ai`（AI 与语音接入）两个库模块。

## 模块划分

| 模块 | 角色 | 说明 |
|---|---|---|
| `ida-common` | 库 | 领域模型、DTO/VO、通用异常、加解密、脱敏 |
| `ida-ai` | 库 | LangChain4j AI Service、计费计量、NLS/Whisper 听写客户端 |
| `ida-app` | 库 | App 端业务（简历、面试、模拟面试、计费、用户） |
| `ida-plugin` | 库 | 插件端业务（一键填表、站点模板、别名、反馈闭环） |
| `ida-admin` | 库 | 管理后台业务 |
| `ida-app-server` | 进程 | App 服务入口，默认 **8088** |
| `ida-plugin-server` | 进程 | 插件服务入口，默认 **8089** |
| `ida-admin-server` | 进程 | 管理后台入口，默认 **8090** |

## 技术栈

Spring Boot 3.2（Java 17）、MyBatis-Plus 3.5、MySQL 8、Redis、LangChain4j、阿里云 OSS / NLS / 短信、Knife4j（OpenAPI）、Docker、nginx。

## 本地开发

### 前置依赖

- JDK 17+、Maven 3.8+
- MySQL 8（默认 `127.0.0.1:3306`，库名 `ida_db`）
- Redis（默认 `127.0.0.1:6379`，无密码）

### 初始化数据库

```bash
mysql -uroot -p -e "create database if not exists ida_db default charset utf8mb4"
# 按版本号顺序执行迁移脚本
for f in database/mysql/ida_v*.sql; do mysql -uroot -p ida_db < "$f"; done
```

### 配置环境变量

最小可运行集合（dev 环境下短信/AI 可先用模拟配置）：

```bash
export MYSQL_PASSWORD=你的数据库密码
# 可选：文件上传解析需要 OSS；AI 功能需要方舟 Key；短信接阿里云
export ALIYUN_OSS_ACCESS_KEY_ID=...   ALIYUN_OSS_ACCESS_KEY_SECRET=...
export ALIYUN_OSS_BUCKET=...          ALIYUN_OSS_ENDPOINT=oss-cn-shenzhen.aliyuncs.com
export ARK_API_KEY_DEEPSEEK=...
export IDA_AES_FIELD_SECRET=...       IDA_AES_FIELD_SALT_HEX=...   # 字段级加密密钥材料
```

完整变量清单见 `deploy/.env.example`。

> dev 环境内置**模拟短信登录**：`mock-code-enabled: true`，手机号 `13800000000` / `13800000001`，固定验证码 `246810`（见 `docs/test-resume.md`）。生产必须关闭。

### 构建与启动

```bash
mvn clean package -DskipTests

java -jar ida-app-server/target/ida-app-server-1.0.0-SNAPSHOT.jar      # App 服务
java -jar ida-plugin-server/target/ida-plugin-server-1.0.0-SNAPSHOT.jar # 插件服务
java -jar ida-admin-server/target/ida-admin-server-1.0.0-SNAPSHOT.jar   # 管理后台
```

默认激活 `dev` profile（`application.yml` 中 `spring.profiles.active`）；生产用 `SPRING_PROFILES_ACTIVE=prod`。

### API 文档（Knife4j）

启动后访问 `http://localhost:8088/doc.html`（插件端 8089、后台 8090 同理），按分组选择「正式客户端 / 插件端 / 管理后台」在线调试。

## Docker 部署（生产）

```bash
cd deploy
cp .env.example .env   # 填入真实值
# 构建并推送镜像（可选）
./push-images.sh
# 编排启动（nginx + 三个服务 + MySQL + Redis）
docker compose up -d
```

详见 `deploy/docker-compose.yml` 与 `deploy/nginx/nginx.conf` 注释。注意 nginx 的 `client_max_body_size` 需 ≥ 应用侧 multipart 上限（音频场景 35MB）。

## 目录结构

```
├── ida-common / ida-ai / ida-app / ida-plugin / ida-admin   # 业务与基础库
├── ida-app-server / ida-plugin-server / ida-admin-server    # 三个可部署进程
├── database/mysql/          # 版本化数据库迁移脚本（ida_v1 ~ v12）
├── deploy/                  # docker-compose、nginx、镜像推送脚本
├── docs/                    # 前端对接文档、接口测试用例、隐私安全基线
└── Dockerfile / Dockerfile.fast
```

## 关键设计

- **三层降级填表流水线**：站点模板（人工金标，0.99）→ 确定性规则匹配（别名表，0.85/0.80）→ LLM 收尾；每层只处理上层未命中字段，详见 `docs/plugin-autofill-async-architecture.md`；
- **异步任务模式**：长耗时 AI 调用统一为「提交返回 taskId + 前端轮询 + 内存任务表」，专用线程池隔离，见 `docs/resume-parse-async-frontend-integration.md`、`docs/interview-voice-summarize-async-frontend-integration.md`；
- **数据安全**：敏感字段 AES-256 字段级加密，手机号确定性加密支持等值查询，LLM 请求占位符脱敏 + 返回还原，见 `docs/privacy-security-baseline.md`；
- **反馈闭环**：插件逐字段填写反馈异步批量落库，聚合命中率/失败原因反哺动态别名表。

## 文档索引

`docs/` 内含各模块前端对接文档、Apifox/Swagger 测试用例、隐私政策与安全基线。测试账号与测试简历：`docs/test-resume.md`。
