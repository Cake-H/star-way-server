# 反馈闭环（Feedback Loop）实现方案

## 一、目标与现状

**现状**：`PluginAutofillFeedbackService.submitFeedback` 把逐字段反馈 `rightPush` 到 Redis List `ida:plugin:autofill:feedback:queue` 后就结束——**无人消费、不入库、无统计**。注释自己写着「后续可添加定时任务消费入库做统计分析」。

**目标**：把这条链路补成完整闭环，使其成为「99% 覆盖率/准确率」目标的**数据基座**——能回答「哪类字段、哪个站点、哪种失败原因最常卡住」，从而驱动站点模板校准、同义词库扩充、提示词迭代。

闭环三段：
```
前端上报 → Redis List（已有）→ 定时消费入库（新增）→ 统计查询接口（新增）
```

## 二、核心设计

### 2.1 新建反馈明细表 `ida_plugin_autofill_feedback`

逐字段粒度，每次填表的每个字段一行。诊断数据，保留近 90 天（清理任务见 2.5）。

```sql
CREATE TABLE `ida_plugin_autofill_feedback` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT UNSIGNED NOT NULL,
    `resume_id`        BIGINT UNSIGNED NOT NULL,
    `task_id`          VARCHAR(64) NOT NULL DEFAULT '',     -- 同步路径无 taskId 留空
    `page_url`         VARCHAR(512) NOT NULL DEFAULT '',
    `site_host`        VARCHAR(191) NOT NULL DEFAULT '',    -- 从 pageUrl 解析的 host，便于按站点聚合
    `site_id`          VARCHAR(64) NOT NULL DEFAULT '',     -- 命中的站点模板 id（前端从 plan 结果透传，可空）
    `selector`         VARCHAR(512) NOT NULL DEFAULT '',
    `client_field_key` VARCHAR(128) NOT NULL DEFAULT '',
    `status`           VARCHAR(16) NOT NULL,                -- filled / skipped / error
    `fail_reason`      VARCHAR(32) NOT NULL DEFAULT '',     -- option_not_found/readonly/date_nav_failed/selector_miss/other
    `actual_value`     VARCHAR(512) NOT NULL DEFAULT '',    -- 前端实际写入值（脱敏：截断长度，不含证件号等敏感全文？见决策点）
    `created_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_feedback_site_field` (`site_host`, `client_field_key`, `status`),
    KEY `idx_feedback_user` (`user_id`, `created_at`),
    KEY `idx_feedback_created` (`created_at`)               -- 清理任务用
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='插件一键填表逐字段反馈明细';
```

**不做软删除**（`deleted_at`）：诊断数据用 `created_at` 轮转清理即可，不必走逻辑删除。

**不做聚合表**：数据量未上来前，统计接口直接对明细表 `GROUP BY` 查询，加好索引即可。数据量上来后再加 `ida_plugin_autofill_feedback_stat` 聚合表（upsert）。

### 2.2 Entity + Mapper（复用现有 MyBatis-Plus 约定）

- Entity `PluginAutofillFeedback` 放 `ida-common/.../domain/plugin/autofill/`，`@TableName("ida_plugin_autofill_feedback")` + `@TableId(AUTO)` + `createdAt`。
- Mapper `PluginAutofillFeedbackMapper extends BaseMapper<PluginAutofillFeedback>` 放 `ida-app/.../module/app/.../mapper/`（与现有 mapper 同位，被 ida-plugin 依赖）。需自定义查询方法做统计（XML 或 `@Select` 注解）。

### 2.3 定时消费器 `PluginAutofillFeedbackConsumer`

- 启动类 `IdaPluginApplication` 加 `@EnableScheduling`。
- `@Component` + `@Scheduled(fixedDelayString = "${ida.plugin.autofill.feedback.consume-interval-ms:30000}")`。
- 每次循环 `LPOP` 一批（最多 `batch-size` 默认 200 条，Redis pipeline 减少往返），逐条反序列化为 entity，批量 `insert`（MyBatis-Plus `saveBatch` 或手写 batch insert）。
- **消费失败策略**：单条 JSON 解析失败 → 跳过并 `log.warn`；DB 批量插入失败 → 记录 error 日志并**丢弃这批**（诊断数据非关键路径，避免毒丸消息堵塞队列；丢失可接受，前端下次填表会再上报）。
- 队列空 → 直接返回，不阻塞。
- 消费器异常被 `@Scheduled` 吞掉不影响下次调度（Spring 默认行为，但加 try-catch 显式保护）。
- `@Scheduled` 注意：单实例跑即可；多实例部署会重复消费——反馈数据重复写入可接受（统计去重靠聚合查询，不靠行唯一）。若后续要严格单消费，加 Redis 分布式锁，本轮不做。

### 2.4 统计查询接口

新增 `GET /api/v1/plugin/resume/autofill-feedback/stats`：
- 入参：`days`（默认 30）、可选 `siteHost` 过滤。
- 出参（按当前用户 `userId` 过滤——见决策点）：
  - `totalFields`、`filledCount`、`skippedCount`、`errorCount`、`fillRate`（命中率）
  - `byFailReason`：`[{reason, count}]` Top 失败原因
  - `byClientFieldKey`：`[{clientFieldKey, total, filled, fillRate}]` 命中率最低的字段 Top 20
  - `bySiteHost`：`[{siteHost, total, filled, fillRate}]` 各站点命中率

SQL 用 `GROUP BY` + `COUNT(CASE WHEN status='filled'...)`，单次查询 + 内存聚合，避免多次扫表。

### 2.5 数据轮转清理（可选，本轮含）

`@Scheduled` 每天凌晨删 `created_at < now()-90d` 的明细。低优先级，避免表无限膨胀。cron 用非整点（如 `17 3 * * *`）。

### 2.6 与站点模板衔接（向后兼容的契约增强）

为了让反馈能关联「命中的模板」，做两处向后兼容的字段增加：
- `PluginAutofillPlanResultVO` 加可选 `siteId`：planAutofill 命中模板时填入，前端可读取。
- `PluginAutofillFeedbackRequest` 加可选 `siteId`：前端从 plan 结果透传回上报。
- 消费入库时写入 `site_id` 列。

前端不传 `siteId` 也能跑（留空），不破坏现有契约。这样后续能统计「模板命中率」「哪些站点该建模板」。

### 2.7 actual_value 脱敏

反馈含 `actualValue`（前端实际写入值），可能含手机号/证件号等敏感信息。入库前**不存全文**：截断到 64 字符，且对疑似手机号/身份证号的值做掩码（保留前 3 后 4）。诊断只需知道「值是否对齐」，不需全文。放消费器入库前处理。

## 三、模块归属

| 模块 | 新增/修改 | 内容 |
|---|---|---|
| `database/mysql` | 新增 | `ida_v3_plugin_feedback.sql` 增量脚本 |
| `ida-common` | 新增 | `PluginAutofillFeedback` entity、`PluginAutofillFeedbackStatVO`（统计出参） |
| `ida-common` | 修改 | `PluginAutofillPlanResultVO` 加 `siteId`；`PluginAutofillFeedbackRequest` 加 `siteId` |
| `ida-app` | 新增 | `PluginAutofillFeedbackMapper`（含统计查询）+ XML |
| `ida-plugin` | 新增 | `PluginAutofillFeedbackConsumer`（定时消费+清理+脱敏）、`PluginAutofillFeedbackStatsService`（统计查询） |
| `ida-plugin` | 修改 | `PluginFormAutofillAiService`：merged 结果回填 `siteId`；`PluginResumeController` 加 stats 接口 |
| `ida-plugin-server` | 修改 | `IdaPluginApplication` 加 `@EnableScheduling` |

## 四、新增文件清单

```
database/mysql/ida_v3_plugin_feedback.sql

ida-common/.../domain/plugin/autofill/
  PluginAutofillFeedback.java                    (entity)
  PluginAutofillFeedbackStatVO.java              (统计出参 VO)

ida-app/.../module/app/plugin/mapper/
  PluginAutofillFeedbackMapper.java
  resources/mapper/PluginAutofillFeedbackMapper.xml   (统计 SQL)

ida-plugin/.../module/plugin/resume/autofill/
  PluginAutofillFeedbackConsumer.java            (定时消费 + 清理 + 脱敏)
  PluginAutofillFeedbackStatsService.java        (统计查询)
```

## 五、修改文件清单

```
ida-common/.../PluginAutofillPlanResultVO.java        + siteId
ida-common/.../PluginAutofillFeedbackRequest.java     + siteId
ida-plugin/.../PluginFormAutofillAiService.java       merged 回填 siteId
ida-plugin/.../controller/PluginResumeController.java + GET stats 接口
ida-plugin-server/.../IdaPluginApplication.java       + @EnableScheduling
ida-plugin-server/src/main/resources/application.yml  + 消费间隔/batch-size 配置项（可选）
```

## 六、不做的事（边界）

- 不做聚合表（数据量未到，GROUP BY 够用）。
- 不做全局/管理员统计后台（见决策点）。
- 不做严格的单消费者分布式锁（多实例重复消费可接受）。
- 不动 `ida_site_fill_rule` 预留表（那是用户级规则，与本闭环正交）。
- 不改前端契约的破坏性变更（siteId 等均为可选新增）。

## 七、验收标准

1. 反馈上报后，30s 内可在 `ida_plugin_autofill_feedback` 表查到明细行。
2. stats 接口返回正确的命中率与 Top 失败原因/字段/站点。
3. `actualValue` 入库前已脱敏（手机号/身份证号掩码）。
4. 命中模板的填表，反馈行 `site_id` 非空（前端透传 siteId 时）。
5. 90 天前数据被清理任务删除。
6. 消费器异常不阻塞下次调度；队列空时不空转浪费。
7. 现有 autofill 主流程零回归（反馈是旁路）。

## 八、决策点（已确认）

1. **stats 接口可见范围**：✅ 用户级——插件端 stats 按 `userId` 过滤，用户看自己的命中率与失败字段；全局运营统计留给后台管理系统（不在本仓库）。
2. **actual_value 脱敏**：✅ 掩码敏感 + 截断——手机号/身份证号保留前 3 后 4 掩码，其余截断到 64 字符。

## 九、后续迭代（备忘）

- 数据量上来后加聚合表 `ida_plugin_autofill_feedback_stat`（按 site_host+client_field_key+day upsert）。
- 从反馈数据自动发现「高频失败站点」→ 提示运营建模板。
- 从 `option_not_found` 高频字段自动扩充 `ChineseFieldAliasTable` 同义词。
- `ida_site_fill_rule` 用户级规则表的代码落地（用户可手动修正映射并持久化）。
